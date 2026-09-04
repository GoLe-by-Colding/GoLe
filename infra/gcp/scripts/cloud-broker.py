#!/usr/bin/env python3
"""Root-only GCP credential broker for the unprivileged budget relay.

The Unix protocol exposes fixed Pub/Sub pull/ack operations but never an OAuth
token. A stop request is re-evaluated from root-owned policy and host metrics;
the caller cannot select a project, subscription, instance or threshold.
"""

from __future__ import annotations

import base64
import json
import os
import pathlib
import re
import signal
import socketserver
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone
from decimal import Decimal, InvalidOperation
from typing import Any


METADATA_TOKEN_URL = (
    "http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token"
)
METADATA_MACHINE_TYPE_URL = (
    "http://169.254.169.254/computeMetadata/v1/instance/machine-type"
)
PUBSUB_ROOT = "https://pubsub.googleapis.com/v1"
CONFIG_PATH = pathlib.Path("/etc/gole/cloud-broker.conf")
SOCKET_PATH = pathlib.Path("/run/gole-cloud-broker/broker.sock")
POLICY_HEARTBEAT_PATH = pathlib.Path("/run/gole-cloud-broker/policy-heartbeat")
STATE_PATH = pathlib.Path("/var/lib/gole-cloud-broker/state.json")
TX_BYTES_PATH = pathlib.Path("/sys/class/net/ens4/statistics/tx_bytes")
DISCORD_ENV_PATH = pathlib.Path("/etc/gole/discord.env")
MAX_REQUEST = 131072
PROTOCOL_VERSION = 1
GIB = Decimal(1024**3)
DEADLINE_ALERT_DAYS = (14, 7, 3, 1)
DEADLINE_ALERT_KEYS = frozenset({*(f"d-{day}" for day in DEADLINE_ALERT_DAYS), "expired"})


class BrokerError(RuntimeError):
    pass


def parse_config() -> dict[str, str]:
    if CONFIG_PATH.is_symlink() or not CONFIG_PATH.is_file():
        raise BrokerError("broker configuration is missing")
    if CONFIG_PATH.stat().st_mode & 0o077:
        raise BrokerError("broker configuration permissions are too broad")
    result: dict[str, str] = {}
    for line in CONFIG_PATH.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Z][A-Z0-9_]*)=([ -~]+)", line)
        if not match or match.group(1) in result:
            raise BrokerError("broker configuration is invalid")
        result[match.group(1)] = match.group(2)
    required = {
        "PROJECT_ID",
        "SUBSCRIPTION",
        "VM_COST_START",
        "HARD_STOP_AT",
        "MAX_RUNTIME_HOURS",
        "FIXED_HOURLY_COST_KRW",
        "HIGH_RATE_HOURLY_COST_KRW",
        "RATE_TRANSITION_AT",
        "EXPECTED_MACHINE_TYPE",
        "SNAPSHOT_MAX_HOURLY_COST_KRW",
        "SNAPSHOT_RETENTION_HOURS",
        "MANUAL_SNAPSHOT_HOURLY_COST_KRW",
        "STOPPED_RESOURCE_HOURLY_COST_KRW",
        "CREDIT_DEADLINE",
        "ALL_IN_LIMIT_KRW",
        "BILLING_LIMIT_KRW",
        "NETWORK_LIMIT_GIB",
        "NETWORK_PERIOD_BASELINE_BYTES",
        "NETWORK_EGRESS_KRW_PER_GIB",
        "VAT_RATE",
        "BUDGET_DISPLAY_NAME",
        "BUDGET_AMOUNT_KRW",
        "BUDGET_PERIOD_START",
        "EXPECTED_BUDGET_ID",
        "EXPECTED_BILLING_ACCOUNT_ID",
    }
    if set(result) != required:
        raise BrokerError("broker configuration keys are invalid")
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", result["PROJECT_ID"]):
        raise BrokerError("broker project is invalid")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9._~-]{2,254}", result["SUBSCRIPTION"]):
        raise BrokerError("broker subscription is invalid")
    if result["BUDGET_DISPLAY_NAME"] != "GoLe production credit guard":
        raise BrokerError("broker budget display name is invalid")
    return result


class Policy:
    def __init__(self, raw: dict[str, str]) -> None:
        self.raw = raw
        try:
            self.vm_cost_start = datetime.fromisoformat(raw["VM_COST_START"])
            self.stop_at = datetime.fromisoformat(raw["HARD_STOP_AT"])
            self.deadline = datetime.fromisoformat(raw["CREDIT_DEADLINE"])
            self.max_runtime = Decimal(raw["MAX_RUNTIME_HOURS"])
            self.fixed_hourly = Decimal(raw["FIXED_HOURLY_COST_KRW"])
            self.high_rate_hourly = Decimal(raw["HIGH_RATE_HOURLY_COST_KRW"])
            self.rate_transition_at = datetime.fromisoformat(raw["RATE_TRANSITION_AT"])
            self.snapshot_hourly = Decimal(raw["SNAPSHOT_MAX_HOURLY_COST_KRW"])
            self.snapshot_retention = Decimal(raw["SNAPSHOT_RETENTION_HOURS"])
            self.manual_snapshot_hourly = Decimal(
                raw["MANUAL_SNAPSHOT_HOURLY_COST_KRW"]
            )
            self.stopped_resource_hourly = Decimal(
                raw["STOPPED_RESOURCE_HOURLY_COST_KRW"]
            )
            self.all_in_limit = Decimal(raw["ALL_IN_LIMIT_KRW"])
            self.billing_limit = Decimal(raw["BILLING_LIMIT_KRW"])
            self.network_limit = Decimal(raw["NETWORK_LIMIT_GIB"])
            self.network_baseline_bytes = int(raw["NETWORK_PERIOD_BASELINE_BYTES"])
            self.egress_rate = Decimal(raw["NETWORK_EGRESS_KRW_PER_GIB"])
            self.vat_rate = Decimal(raw["VAT_RATE"])
            self.budget_amount = Decimal(raw["BUDGET_AMOUNT_KRW"])
            self.period_start = date.fromisoformat(raw["BUDGET_PERIOD_START"])
        except (InvalidOperation, ValueError) as exc:
            raise BrokerError("broker numeric or time policy is invalid") from exc
        for value in (
            self.vm_cost_start,
            self.rate_transition_at,
            self.stop_at,
            self.deadline,
        ):
            if value.tzinfo is None:
                raise BrokerError("broker timestamps require timezones")
        if not self.vm_cost_start < self.rate_transition_at < self.stop_at <= self.deadline:
            raise BrokerError("broker time policy order is invalid")
        if not Decimal("0") < self.max_runtime <= Decimal("1350"):
            raise BrokerError("broker runtime limit is invalid")
        if self.all_in_limit > Decimal("350000") or self.billing_limit > Decimal("320000"):
            raise BrokerError("broker monetary stop limits are too high")
        positive = (
            self.fixed_hourly,
            self.high_rate_hourly,
            self.snapshot_hourly,
            self.snapshot_retention,
            self.manual_snapshot_hourly,
            self.stopped_resource_hourly,
            self.all_in_limit,
            self.billing_limit,
            self.network_limit,
            self.egress_rate,
            self.budget_amount,
        )
        if any(value <= 0 for value in positive):
            raise BrokerError("broker positive cost policy is invalid")
        if self.vat_rate != Decimal("0.10"):
            raise BrokerError("broker VAT policy is invalid")
        if self.snapshot_retention != Decimal("72"):
            raise BrokerError("broker snapshot retention is invalid")
        if self.network_baseline_bytes != 536870912:
            raise BrokerError("broker network baseline is invalid")
        if raw["EXPECTED_MACHINE_TYPE"] != "e2-standard-2":
            raise BrokerError("broker machine type is invalid")
        exact = {
            "VM_COST_START": "2026-09-01T19:57:05+09:00",
            "HARD_STOP_AT": "2026-10-28T01:50:00+09:00",
            "MAX_RUNTIME_HOURS": "1350",
            "FIXED_HOURLY_COST_KRW": "153.390555330",
            "HIGH_RATE_HOURLY_COST_KRW": "240.749900000",
            "RATE_TRANSITION_AT": "2026-09-06T00:00:00+09:00",
            "SNAPSHOT_MAX_HOURLY_COST_KRW": "39.041010000",
            "SNAPSHOT_RETENTION_HOURS": "72",
            "MANUAL_SNAPSHOT_HOURLY_COST_KRW": "13.013670000",
            "STOPPED_RESOURCE_HOURLY_COST_KRW": "45.725095000",
            "CREDIT_DEADLINE": "2026-10-28T23:59:59+09:00",
            "ALL_IN_LIMIT_KRW": "350000",
            "BILLING_LIMIT_KRW": "320000",
            "NETWORK_LIMIT_GIB": "30",
            "NETWORK_PERIOD_BASELINE_BYTES": "536870912",
            "NETWORK_EGRESS_KRW_PER_GIB": "318.154399937",
            "VAT_RATE": "0.10",
            "BUDGET_AMOUNT_KRW": "370000",
            "BUDGET_PERIOD_START": "2026-09-01",
        }
        if any(raw.get(key) != value for key, value in exact.items()):
            raise BrokerError("broker policy differs from the reviewed cost arm")
        if not re.fullmatch(
            r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
            raw["EXPECTED_BUDGET_ID"],
        ):
            raise BrokerError("broker expected budget ID is invalid")
        if not re.fullmatch(
            r"[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}",
            raw["EXPECTED_BILLING_ACCOUNT_ID"],
        ):
            raise BrokerError("broker expected billing account ID is invalid")

    @property
    def subscription_path(self) -> str:
        return f"projects/{self.raw['PROJECT_ID']}/subscriptions/{self.raw['SUBSCRIPTION']}"

    def stop_reason(
        self, latest_billing: Decimal, now: datetime, cumulative_tx_bytes: int
    ) -> str | None:
        _current, _tail, all_in, runtime, tx_gib = self.projected_all_in(
            latest_billing, now, cumulative_tx_bytes
        )
        if now >= self.stop_at:
            return "absolute-cutoff"
        if runtime >= self.max_runtime:
            return "runtime-limit"
        if tx_gib >= self.network_limit:
            return "network-limit"
        if latest_billing >= self.billing_limit:
            return "billing-limit"
        if all_in >= self.all_in_limit:
            return "all-in-limit"
        return None

    def projected_all_in(
        self, latest_billing: Decimal, now: datetime, cumulative_tx_bytes: int
    ) -> tuple[Decimal, Decimal, Decimal, Decimal, Decimal]:
        runtime = max(
            Decimal("0"),
            Decimal(str((now - self.vm_cost_start).total_seconds())) / Decimal("3600"),
        )
        if cumulative_tx_bytes < 0:
            raise BrokerError("host network meter is unavailable")
        tx_gib = Decimal(cumulative_tx_bytes) / GIB
        remaining = max(
            Decimal("0"),
            Decimal(str((self.deadline - now).total_seconds())) / Decimal("3600"),
        )
        snapshot_tail = (
            self.snapshot_hourly * min(remaining, self.snapshot_retention)
            + self.manual_snapshot_hourly * remaining
        )
        stopped_tail = self.stopped_resource_hourly * remaining
        high_rate_hours = min(
            runtime,
            Decimal(
                str(
                    (self.rate_transition_at - self.vm_cost_start).total_seconds()
                    / 3600
                )
            ),
        )
        current = max(
            latest_billing,
            self.high_rate_hourly * high_rate_hours
            + self.fixed_hourly * max(Decimal("0"), runtime - high_rate_hours)
            + (self.snapshot_hourly + self.manual_snapshot_hourly) * runtime
            + self.egress_rate * tx_gib,
        )
        all_in = (current + snapshot_tail + stopped_tail) * (
            Decimal("1") + self.vat_rate
        )
        return current, snapshot_tail + stopped_tail, all_in, runtime, tx_gib


class Cloud:
    def __init__(self, policy: Policy) -> None:
        self.policy = policy
        self.token = ""
        self.expires_at = 0.0
        self.lock = threading.Lock()
        (
            self.latest_billing,
            self.last_tx_raw,
            self.cumulative_tx_bytes,
            self.deadline_alerts,
            needs_meter_migration,
            needs_schema_write,
        ) = self._read_state()
        current_raw = self._read_raw_tx_bytes()
        if needs_meter_migration:
            # Existing broker state did not retain a reboot-safe NIC meter.
            # Seed it with a reviewed 0.5 GiB period baseline plus the current
            # boot counter. This may double-count a small amount, never under-
            # count the known pre-migration traffic.
            self.last_tx_raw = current_raw
            self.cumulative_tx_bytes = (
                self.policy.network_baseline_bytes + current_raw
            )
            self._write_state()
        else:
            meter_state_written = self._update_network_meter(current_raw)
            if needs_schema_write and not meter_state_written:
                self._write_state()

    def _read_state(self) -> tuple[Decimal, int, int, set[str], bool, bool]:
        if not STATE_PATH.exists() and not STATE_PATH.is_symlink():
            return Decimal("0"), 0, 0, set(), True, False
        if STATE_PATH.is_symlink() or not STATE_PATH.is_file():
            raise BrokerError("broker state metadata is invalid")
        try:
            raw = json.loads(STATE_PATH.read_text(encoding="utf-8"))
            if set(raw) == {"latest_billing"}:
                latest = Decimal(str(raw["latest_billing"]))
                if latest < 0:
                    raise BrokerError("broker legacy billing state is invalid")
                return latest, 0, 0, set(), True, False
            old_keys = {"latest_billing", "last_tx_raw", "cumulative_tx_bytes"}
            new_keys = {*old_keys, "deadline_alerts"}
            if set(raw) not in (old_keys, new_keys):
                raise BrokerError("broker state keys are invalid")
            latest = Decimal(str(raw["latest_billing"]))
            last_raw = int(raw["last_tx_raw"])
            cumulative = int(raw["cumulative_tx_bytes"])
            if latest < 0 or last_raw < 0 or cumulative < last_raw:
                raise BrokerError("broker state values are invalid")
            alert_values = raw.get("deadline_alerts", [])
            if (
                not isinstance(alert_values, list)
                or any(not isinstance(value, str) for value in alert_values)
                or len(alert_values) != len(set(alert_values))
                or not set(alert_values).issubset(DEADLINE_ALERT_KEYS)
            ):
                raise BrokerError("broker deadline alert state is invalid")
            return (
                latest,
                last_raw,
                cumulative,
                set(alert_values),
                False,
                set(raw) == old_keys,
            )
        except (OSError, ValueError, TypeError, InvalidOperation) as exc:
            raise BrokerError("broker state is corrupt") from exc

    @staticmethod
    def _read_raw_tx_bytes() -> int:
        try:
            raw = TX_BYTES_PATH.read_text(encoding="ascii").strip()
            if not re.fullmatch(r"[0-9]{1,24}", raw):
                raise ValueError("invalid NIC counter")
            return int(raw)
        except (OSError, ValueError) as exc:
            raise BrokerError("host network meter is unavailable") from exc

    def _update_network_meter(self, current_raw: int | None = None) -> bool:
        if current_raw is None:
            current_raw = self._read_raw_tx_bytes()
        delta = (
            current_raw - self.last_tx_raw
            if current_raw >= self.last_tx_raw
            else current_raw
        )
        if delta < 0:
            raise BrokerError("host network meter moved backwards")
        if delta:
            self.cumulative_tx_bytes += delta
        changed = current_raw != self.last_tx_raw or delta != 0
        self.last_tx_raw = current_raw
        if changed:
            self._write_state()
        return changed

    def _write_state(self) -> None:
        STATE_PATH.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        if STATE_PATH.parent.is_symlink() or (STATE_PATH.parent.stat().st_mode & 0o077):
            raise BrokerError("broker state directory permissions are invalid")
        temporary = STATE_PATH.with_suffix(".tmp")
        payload = json.dumps(
            {
                "latest_billing": str(self.latest_billing),
                "last_tx_raw": self.last_tx_raw,
                "cumulative_tx_bytes": self.cumulative_tx_bytes,
                "deadline_alerts": sorted(self.deadline_alerts),
            },
            separators=(",", ":"),
        ).encode("ascii") + b"\n"
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_CLOEXEC | os.O_NOFOLLOW,
            0o600,
        )
        try:
            os.fchmod(descriptor, 0o600)
            written = 0
            while written < len(payload):
                count = os.write(descriptor, payload[written:])
                if count <= 0:
                    raise BrokerError("broker state write was incomplete")
                written += count
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        os.replace(temporary, STATE_PATH)
        directory = os.open(STATE_PATH.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)

    @staticmethod
    def _operations_webhook_url() -> str:
        if DISCORD_ENV_PATH.is_symlink() or not DISCORD_ENV_PATH.is_file():
            raise BrokerError("operations notification configuration is missing")
        metadata = DISCORD_ENV_PATH.stat()
        if metadata.st_uid != 0 or metadata.st_gid != 0 or metadata.st_mode & 0o077:
            raise BrokerError("operations notification configuration is not root-only")
        matches: list[str] = []
        try:
            for line in DISCORD_ENV_PATH.read_text(encoding="utf-8").splitlines():
                if line.startswith("DISCORD_OPERATIONS_WEBHOOK_URL="):
                    matches.append(line.split("=", 1)[1])
        except OSError as exc:
            raise BrokerError("operations notification configuration is unreadable") from exc
        if len(matches) != 1:
            raise BrokerError("operations webhook configuration is invalid")
        url = matches[0]
        parsed = urllib.parse.urlsplit(url)
        if (
            parsed.scheme != "https"
            or parsed.hostname not in {"discord.com", "discordapp.com"}
            or parsed.username is not None
            or parsed.password is not None
            or parsed.port not in (None, 443)
            or not re.fullmatch(r"/api/webhooks/[0-9]{15,24}/[A-Za-z0-9._-]{40,200}", parsed.path)
            or parsed.query
            or parsed.fragment
        ):
            raise BrokerError("operations webhook configuration is invalid")
        return url

    def _send_deadline_alert(self, key: str, now: datetime) -> bool:
        try:
            webhook_url = self._operations_webhook_url()
            if key == "expired":
                headline = "⚠️ GCP 크레딧 기한이 지났습니다."
            else:
                headline = f"⏳ GCP 크레딧 기한 {key.upper()} 운영 알림"
            content = (
                f"{headline}\n"
                f"- 기준 시각: {now.astimezone(self.policy.deadline.tzinfo).isoformat(timespec='seconds')}\n"
                "- VM 정지 뒤에도 디스크·고정 IP·수동 스냅샷 비용은 계속 발생함\n"
                "- 데이터 자원은 자동 삭제하지 않음; 운영자가 이전·복구 검증 후 명시적으로 정리해야 함"
            )
            request = urllib.request.Request(
                webhook_url,
                data=json.dumps(
                    {
                        "content": content,
                        "allowed_mentions": {"parse": []},
                        "flags": 4096,
                    }
                ).encode("utf-8"),
                method="POST",
                headers={"Content-Type": "application/json"},
            )
            with urllib.request.urlopen(request, timeout=5) as response:
                if not 200 <= response.status < 300:
                    return False
            return True
        except (BrokerError, OSError, ValueError, urllib.error.URLError):
            # Availability of Discord must never weaken the independent stop
            # policy. Keep the reminder unmarked so a later loop retries.
            return False

    def _notify_deadline_reminders(self, now: datetime) -> None:
        remaining_seconds = (self.policy.deadline - now).total_seconds()
        pending: list[str] = []
        for days in DEADLINE_ALERT_DAYS:
            key = f"d-{days}"
            if key not in self.deadline_alerts and remaining_seconds <= days * 86400:
                pending.append(key)
        if remaining_seconds <= 0 and "expired" not in self.deadline_alerts:
            pending.append("expired")
        for key in pending:
            if self._send_deadline_alert(key, now):
                self.deadline_alerts.add(key)
                self._write_state()

    def _access_token(self, refresh: bool = False) -> str:
        if not refresh and self.token and time.monotonic() < self.expires_at:
            return self.token
        request = urllib.request.Request(
            METADATA_TOKEN_URL, headers={"Metadata-Flavor": "Google"}
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                payload = json.load(response)
            self.token = str(payload["access_token"])
            self.expires_at = time.monotonic() + max(1, int(payload["expires_in"]) - 60)
            return self.token
        except (OSError, KeyError, TypeError, ValueError) as exc:
            raise BrokerError("metadata credential request failed") from exc

    def _post(self, action: str, payload: dict[str, Any]) -> dict[str, Any]:
        endpoint = (
            f"{PUBSUB_ROOT}/{urllib.parse.quote(self.policy.subscription_path, safe='/')}:{action}"
        )
        for attempt in range(2):
            request = urllib.request.Request(
                endpoint,
                data=json.dumps(payload).encode("utf-8"),
                method="POST",
                headers={
                    "Authorization": f"Bearer {self._access_token(attempt > 0)}",
                    "Content-Type": "application/json",
                },
            )
            try:
                with urllib.request.urlopen(request, timeout=15) as response:
                    body = response.read()
                return json.loads(body) if body else {}
            except urllib.error.HTTPError as exc:
                if exc.code == 401 and attempt == 0:
                    continue
                raise BrokerError(f"fixed Pub/Sub {action} failed") from exc
            except (OSError, ValueError) as exc:
                raise BrokerError(f"fixed Pub/Sub {action} failed") from exc
        raise BrokerError("fixed Pub/Sub authorization failed")

    def _observe_budget(self, messages: list[dict[str, Any]]) -> None:
        changed = False
        for received in messages:
            try:
                message = received["message"]
                attributes = message["attributes"]
                payload = json.loads(base64.b64decode(message["data"], validate=True))
                amount = Decimal(str(payload["costAmount"]))
                interval = datetime.fromisoformat(
                    str(payload["costIntervalStart"]).replace("Z", "+00:00")
                )
                valid = (
                    attributes.get("schemaVersion") == "1.0"
                    and attributes.get("budgetId")
                    == self.policy.raw["EXPECTED_BUDGET_ID"]
                    and attributes.get("billingAccountId")
                    == self.policy.raw["EXPECTED_BILLING_ACCOUNT_ID"]
                    and payload.get("budgetDisplayName") == self.policy.raw["BUDGET_DISPLAY_NAME"]
                    and payload.get("currencyCode") == "KRW"
                    and Decimal(str(payload["budgetAmount"])) == self.policy.budget_amount
                    and interval.date() == self.policy.period_start
                    and amount >= self.latest_billing
                )
                if valid:
                    self.latest_billing = amount
                    changed = True
            except (KeyError, TypeError, ValueError, InvalidOperation):
                continue
        if changed:
            self._write_state()

    def handle(self, request: dict[str, Any]) -> dict[str, Any]:
        with self.lock:
            operation = request.get("operation")
            if operation == "readiness" and set(request) == {"operation"}:
                # This fixed, side-effect-free exchange lets systemd prove that
                # the process serving the Unix socket understands the newly
                # installed protocol.  In particular, a bootstrap upgrade must
                # not accept the stale process left alive by `enable --now`.
                return {"ready": True, "protocol_version": PROTOCOL_VERSION}
            if operation == "pull" and set(request) == {"operation", "max_messages"}:
                maximum = request["max_messages"]
                if not isinstance(maximum, int) or not 1 <= maximum <= 20:
                    raise BrokerError("invalid pull bound")
                response = self._post("pull", {"maxMessages": maximum})
                messages = response.get("receivedMessages", [])
                if not isinstance(messages, list):
                    raise BrokerError("invalid Pub/Sub response")
                self._observe_budget(messages)
                return {"messages": messages}
            if operation == "acknowledge" and set(request) == {"operation", "ack_ids"}:
                ack_ids = request["ack_ids"]
                if (
                    not isinstance(ack_ids, list)
                    or len(ack_ids) > 20
                    or any(not isinstance(value, str) or not 1 <= len(value) <= 4096 for value in ack_ids)
                ):
                    raise BrokerError("invalid acknowledgement list")
                if ack_ids:
                    self._post("acknowledge", {"ackIds": ack_ids})
                return {"acknowledged": len(ack_ids)}
            if operation == "stop" and set(request) == {"operation", "request_id"}:
                if not re.fullmatch(
                    r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                    str(request["request_id"]),
                ):
                    raise BrokerError("invalid stop request")
                self._update_network_meter()
                reason = self.policy.stop_reason(
                    self.latest_billing,
                    datetime.now(timezone.utc),
                    self.cumulative_tx_bytes,
                )
                if reason is None:
                    raise BrokerError("independent host stop policy is not met")
                subprocess.run(
                    ["/usr/bin/systemctl", "poweroff", "--no-block"],
                    check=True,
                    timeout=5,
                )
                return {"accepted": True, "reason": reason}
            raise BrokerError("unsupported broker operation")

    def enforce_host_policy(self) -> None:
        with self.lock:
            now = datetime.now(timezone.utc)
            self._update_network_meter()
            self._notify_deadline_reminders(now)
            if now >= self.policy.rate_transition_at:
                request = urllib.request.Request(
                    METADATA_MACHINE_TYPE_URL,
                    headers={"Metadata-Flavor": "Google"},
                )
                try:
                    with urllib.request.urlopen(request, timeout=3) as response:
                        machine_type = response.read().decode("ascii").strip().rsplit("/", 1)[-1]
                except OSError as exc:
                    raise BrokerError("machine type attestation failed") from exc
                if machine_type != self.policy.raw["EXPECTED_MACHINE_TYPE"]:
                    subprocess.run(
                        ["/usr/bin/systemctl", "poweroff", "--no-block"],
                        check=True,
                        timeout=5,
                    )
                    return
            reason = self.policy.stop_reason(
                self.latest_billing, now, self.cumulative_tx_bytes
            )
            if reason is None:
                return
            subprocess.run(
                ["/usr/bin/systemctl", "poweroff", "--no-block"],
                check=True,
                timeout=5,
            )


class Handler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        raw = self.rfile.readline(MAX_REQUEST + 1)
        if not raw or len(raw) > MAX_REQUEST or not raw.endswith(b"\n"):
            return
        try:
            request = json.loads(raw)
            if not isinstance(request, dict):
                raise BrokerError("request must be an object")
            response = {"ok": True, "result": self.server.cloud.handle(request)}  # type: ignore[attr-defined]
        except (BrokerError, ValueError, OSError):
            response = {"ok": False, "error": "broker request rejected"}
        self.wfile.write(json.dumps(response, separators=(",", ":")).encode("utf-8") + b"\n")


class Server(socketserver.ThreadingUnixStreamServer):
    daemon_threads = True

    def __init__(self, cloud: Cloud) -> None:
        SOCKET_PATH.parent.mkdir(mode=0o710, parents=True, exist_ok=True)
        if SOCKET_PATH.exists() or SOCKET_PATH.is_symlink():
            SOCKET_PATH.unlink()
        self.cloud = cloud
        super().__init__(str(SOCKET_PATH), Handler)
        os.chmod(SOCKET_PATH, 0o660)


def enforce_policy_and_write_heartbeat(cloud: Cloud) -> None:
    """Publish liveness only after one complete root policy evaluation."""
    cloud.enforce_host_policy()
    descriptor = os.open(
        POLICY_HEARTBEAT_PATH,
        os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW,
        0o600,
    )
    try:
        os.fchmod(descriptor, 0o600)
        os.write(descriptor, b"ok\n")
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def main() -> int:
    policy = Policy(parse_config())
    server = Server(Cloud(policy))
    policy_stop = threading.Event()

    def policy_loop() -> None:
        # This loop is the trusted hard-stop boundary. The unprivileged relay
        # may be compromised or wedged and is not required to request a stop.
        # A root-only heartbeat lets the independent watchdog prove this loop,
        # rather than merely the container, is still enforcing the policy.
        while not policy_stop.is_set():
            try:
                enforce_policy_and_write_heartbeat(server.cloud)
            except (BrokerError, OSError, subprocess.SubprocessError):
                # systemd restarts a failed broker; the separate host watchdog
                # also powers off if this trusted service becomes unavailable.
                os._exit(1)
            if policy_stop.wait(10):
                return

    policy_thread = threading.Thread(target=policy_loop, name="host-cost-policy", daemon=True)
    policy_thread.start()
    def stop_handler(_signum: int, _frame: Any) -> None:
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, stop_handler)
    signal.signal(signal.SIGINT, stop_handler)
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        policy_stop.set()
        policy_thread.join(timeout=2)
        server.server_close()
        if SOCKET_PATH.exists():
            SOCKET_PATH.unlink()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
