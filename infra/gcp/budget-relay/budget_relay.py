#!/usr/bin/env python3
"""Google Cloud Billing Budget Pub/Sub to Discord relay.

The service deliberately uses only Python's standard library. A root-owned
Unix broker performs the fixed cloud operations without returning an OAuth
token to this container. Messages are acknowledged only after any required
Discord notification and durable deduplication state have completed.
"""

from __future__ import annotations

import base64
import functools
import hashlib
import json
import logging
import os
import random
import signal
import socket
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import date, datetime, time as datetime_time, timedelta, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Callable, Iterable


LOG = logging.getLogger("gole-budget-relay")
METADATA_ROOT = "http://metadata.google.internal/computeMetadata/v1"
PUBSUB_ROOT = "https://pubsub.googleapis.com/v1"
COMPUTE_ROOT = "https://compute.googleapis.com/compute/v1"
STATE_VERSION = 1
GIB = Decimal(1024**3)
SNAPSHOT_RECOVERY_POINT_COUNT = Decimal("3")


class ConfigurationError(ValueError):
    """Raised when required configuration is missing or invalid."""


class RemoteServiceError(RuntimeError):
    """A safe-to-log remote error that never contains a credential URL."""


class PullIdleTimeout(RemoteServiceError):
    """The bounded unary pull returned no message before the client deadline."""


def _decimal(value: Any, field: str) -> Decimal:
    if isinstance(value, bool):
        raise ValueError(f"{field} must be numeric")
    try:
        result = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError) as exc:
        raise ValueError(f"{field} must be numeric") from exc
    if not result.is_finite():
        raise ValueError(f"{field} must be finite")
    return result


def _parse_rfc3339(value: str, field: str) -> datetime:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{field} must be an RFC3339 timestamp")
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise ValueError(f"{field} must be an RFC3339 timestamp") from exc
    if parsed.tzinfo is None:
        raise ValueError(f"{field} must include a timezone")
    return parsed.astimezone(timezone.utc)


def _format_rfc3339(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _boolean(value: str, field: str) -> bool:
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ConfigurationError(f"{field} must be true or false")


def _synchronized(method: Callable[..., Any]) -> Callable[..., Any]:
    """Serialize access to the small shared state object."""

    @functools.wraps(method)
    def wrapper(self: "StateStore", *args: Any, **kwargs: Any) -> Any:
        with self._lock:
            return method(self, *args, **kwargs)

    return wrapper


@dataclass(frozen=True)
class BudgetNotification:
    display_name: str
    cost_amount: Decimal
    budget_amount: Decimal
    currency_code: str
    interval_start: datetime
    amount_type: str
    alert_threshold: Decimal | None = None
    forecast_threshold: Decimal | None = None

    @classmethod
    def from_pubsub_data(cls, encoded_data: str) -> "BudgetNotification":
        if not isinstance(encoded_data, str) or not encoded_data:
            raise ValueError("Pub/Sub message data is missing")
        try:
            raw = base64.b64decode(encoded_data, validate=True)
            payload = json.loads(raw.decode("utf-8"), parse_float=Decimal)
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("Pub/Sub message data is not valid budget JSON") from exc
        if not isinstance(payload, dict):
            raise ValueError("budget payload must be a JSON object")

        required = (
            "budgetDisplayName",
            "costAmount",
            "budgetAmount",
            "currencyCode",
            "costIntervalStart",
        )
        missing = [key for key in required if key not in payload]
        if missing:
            raise ValueError("budget payload is missing: " + ", ".join(missing))

        display_name = payload["budgetDisplayName"]
        currency_code = payload["currencyCode"]
        if not isinstance(display_name, str) or not display_name.strip():
            raise ValueError("budgetDisplayName must be a non-empty string")
        if not isinstance(currency_code, str) or len(currency_code.strip()) != 3:
            raise ValueError("currencyCode must be a three-letter code")

        alert = payload.get("alertThresholdExceeded")
        forecast = payload.get("forecastThresholdExceeded")
        notification = cls(
            display_name=display_name.strip(),
            cost_amount=_decimal(payload["costAmount"], "costAmount"),
            budget_amount=_decimal(payload["budgetAmount"], "budgetAmount"),
            currency_code=currency_code.upper(),
            interval_start=_parse_rfc3339(
                payload["costIntervalStart"], "costIntervalStart"
            ),
            amount_type=str(payload.get("budgetAmountType", "UNKNOWN")),
            alert_threshold=(
                _decimal(alert, "alertThresholdExceeded")
                if alert is not None
                else None
            ),
            forecast_threshold=(
                _decimal(forecast, "forecastThresholdExceeded")
                if forecast is not None
                else None
            ),
        )
        if notification.cost_amount < 0 or notification.budget_amount < 0:
            raise ValueError("budget and cost amounts cannot be negative")
        return notification

    @property
    def period_key(self) -> str:
        identity = "\x00".join(
            (
                self.display_name,
                _format_rfc3339(self.interval_start),
                self.currency_code,
            )
        )
        return hashlib.sha256(identity.encode("utf-8")).hexdigest()[:24]

    @property
    def actual_ratio(self) -> Decimal:
        if self.budget_amount <= 0:
            return Decimal("0")
        return self.cost_amount / self.budget_amount


@dataclass(frozen=True)
class HardStopPolicy:
    enabled: bool
    dry_run: bool
    billing_cost_limit_krw: Decimal
    all_in_cost_limit_krw: Decimal
    all_in_warning_krw: Decimal
    all_in_danger_krw: Decimal
    network_limit_bytes: int
    network_warning_bytes: int
    network_danger_bytes: int
    max_runtime_hours: Decimal
    runtime_warning_hours: Decimal
    runtime_danger_hours: Decimal
    minimum_reserve_krw: Decimal
    expected_budget_amount_krw: Decimal
    expected_budget_id: str
    billing_account_id: str
    budget_display_name: str
    period_start: date
    vm_cost_start: datetime
    runtime_rate_transition_at: datetime
    high_rate_hourly_cost_krw: Decimal
    stop_at: datetime
    arm_id: str
    project_id: str
    zone: str
    instance_name: str
    vat_rate: Decimal
    network_egress_krw_per_gib: Decimal
    stopped_resource_hourly_cost_krw: Decimal
    tx_bytes_path: Path
    boot_id_path: Path
    check_interval_seconds: float
    retry_seconds: float

    def validate_message(
        self,
        budget: BudgetNotification,
        attributes: Any,
    ) -> str | None:
        """Return a safe reason when a message is not the armed production Budget."""
        if not self.enabled:
            return None
        if not isinstance(attributes, dict):
            return "missing attributes"
        expected = {
            "budgetId": self.expected_budget_id,
            "billingAccountId": self.billing_account_id,
            "schemaVersion": "1.0",
        }
        for key, value in expected.items():
            if attributes.get(key) != value:
                return f"unexpected {key}"
        if budget.display_name != self.budget_display_name:
            return "unexpected budget display name"
        if budget.currency_code != "KRW":
            return "unexpected currency"
        if budget.budget_amount != self.expected_budget_amount_krw:
            return "unexpected budget amount"
        if budget.interval_start.date() != self.period_start:
            return "unexpected budget period"
        return None


@dataclass(frozen=True)
class Config:
    subscription: str
    webhook_url: str
    credit_amount_krw: Decimal
    credit_deadline: datetime
    fixed_hourly_cost_krw: Decimal
    snapshot_max_hourly_cost_krw: Decimal
    snapshot_retention_hours: Decimal
    manual_snapshot_hourly_cost_krw: Decimal
    warning_thresholds: tuple[Decimal, ...]
    state_dir: Path
    timezone_offset_hours: int
    max_messages: int
    pull_interval_seconds: float
    http_timeout_seconds: float
    project_id: str | None = None
    hard_stop: HardStopPolicy | None = None

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> "Config":
        values = os.environ if env is None else env

        def required(name: str) -> str:
            value = values.get(name, "").strip()
            if not value:
                raise ConfigurationError(f"{name} is required")
            return value

        hard_stop_enabled = _boolean(
            values.get("GCP_HARD_STOP_ENABLED", "false"),
            "GCP_HARD_STOP_ENABLED",
        )
        subscription = required("GCP_BUDGET_PUBSUB_SUBSCRIPTION")
        webhook_url = values.get("DISCORD_OPERATIONS_WEBHOOK_URL", "").strip()
        webhook_valid = webhook_url.startswith(
            "https://discord.com/api/webhooks/"
        ) or webhook_url.startswith("https://discordapp.com/api/webhooks/")
        if not webhook_valid:
            if not hard_stop_enabled:
                raise ConfigurationError(
                    "DISCORD_OPERATIONS_WEBHOOK_URL is not a Discord webhook"
                )
            if webhook_url:
                LOG.error("유효하지 않은 Discord webhook을 비활성화합니다")
            webhook_url = ""

        credit = _decimal(required("GCP_CREDIT_AMOUNT_KRW"), "GCP_CREDIT_AMOUNT_KRW")
        hourly = _decimal(
            required("GCP_FIXED_HOURLY_COST_KRW"), "GCP_FIXED_HOURLY_COST_KRW"
        )
        snapshot_hourly = _decimal(
            values.get("GCP_SNAPSHOT_MAX_HOURLY_COST_KRW", "0"),
            "GCP_SNAPSHOT_MAX_HOURLY_COST_KRW",
        )
        snapshot_retention_hours = _decimal(
            values.get("GCP_SNAPSHOT_RETENTION_HOURS", "0"),
            "GCP_SNAPSHOT_RETENTION_HOURS",
        )
        manual_snapshot_hourly = _decimal(
            values.get("GCP_MANUAL_SNAPSHOT_HOURLY_COST_KRW", "0"),
            "GCP_MANUAL_SNAPSHOT_HOURLY_COST_KRW",
        )
        if (
            credit <= 0
            or hourly < 0
            or snapshot_hourly < 0
            or snapshot_retention_hours < 0
            or manual_snapshot_hourly < 0
        ):
            raise ConfigurationError("credit must be positive and hourly cost non-negative")

        offset = int(values.get("BUDGET_TIMEZONE_OFFSET_HOURS", "9"))
        if not -23 <= offset <= 23:
            raise ConfigurationError("BUDGET_TIMEZONE_OFFSET_HOURS must be -23..23")
        local_tz = timezone(timedelta(hours=offset))
        deadline_text = required("GCP_CREDIT_DEADLINE")
        try:
            if "T" in deadline_text:
                deadline = datetime.fromisoformat(deadline_text.replace("Z", "+00:00"))
                if deadline.tzinfo is None:
                    deadline = deadline.replace(tzinfo=local_tz)
                deadline = deadline.astimezone(local_tz)
            else:
                # Conservatively count the full expiration date when projecting cost.
                deadline_date = date.fromisoformat(deadline_text)
                deadline = datetime.combine(
                    deadline_date, datetime_time.max, tzinfo=local_tz
                )
        except ValueError as exc:
            raise ConfigurationError(
                "GCP_CREDIT_DEADLINE must be YYYY-MM-DD or ISO8601"
            ) from exc

        project_id = values.get("GCP_PROJECT_ID", "").strip() or None
        hard_stop_dry_run = _boolean(
            values.get("GCP_HARD_STOP_DRY_RUN", "false"),
            "GCP_HARD_STOP_DRY_RUN",
        )

        def hard_required(name: str) -> str:
            value = values.get(name, "").strip()
            if hard_stop_enabled and not value:
                raise ConfigurationError(f"{name} is required when hard stop is enabled")
            return value

        def hard_decimal(name: str, default: str) -> Decimal:
            return _decimal(values.get(name, default), name)

        def hard_datetime(name: str) -> datetime:
            raw = hard_required(name)
            if not raw:
                return datetime(1970, 1, 1, tzinfo=local_tz)
            try:
                parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
            except ValueError as exc:
                raise ConfigurationError(f"{name} must be ISO8601") from exc
            if parsed.tzinfo is None:
                raise ConfigurationError(f"{name} must include a timezone")
            return parsed.astimezone(local_tz)

        try:
            period_start_text = hard_required("GCP_HARD_STOP_PERIOD_START")
            period_start = (
                date.fromisoformat(period_start_text)
                if period_start_text
                else date(1970, 1, 1)
            )
        except ValueError as exc:
            raise ConfigurationError(
                "GCP_HARD_STOP_PERIOD_START must be YYYY-MM-DD"
            ) from exc

        billing_limit = hard_decimal("GCP_HARD_STOP_BILLING_COST_KRW", "320000")
        all_in_limit = hard_decimal("GCP_HARD_STOP_ALL_IN_COST_KRW", "350000")
        all_in_warning = hard_decimal("GCP_COST_GUARD_WARNING_KRW", "330000")
        all_in_danger = hard_decimal("GCP_COST_GUARD_DANGER_KRW", "340000")
        minimum_reserve = hard_decimal("GCP_HARD_STOP_MIN_RESERVE_KRW", "75000")
        network_limit_gib = hard_decimal("GCP_HARD_STOP_NETWORK_GIB", "30")
        network_warning_gib = hard_decimal("GCP_COST_GUARD_NETWORK_WARNING_GIB", "15")
        network_danger_gib = hard_decimal("GCP_COST_GUARD_NETWORK_DANGER_GIB", "25")
        runtime_limit = hard_decimal("GCP_HARD_STOP_MAX_RUNTIME_HOURS", "1350")
        runtime_warning = hard_decimal("GCP_COST_GUARD_RUNTIME_WARNING_HOURS", "1250")
        runtime_danger = hard_decimal("GCP_COST_GUARD_RUNTIME_DANGER_HOURS", "1320")
        vat_rate = hard_decimal("GCP_VAT_RATE", "0.10")
        egress_rate = hard_decimal(
            "GCP_NETWORK_EGRESS_KRW_PER_GIB", "318.154399937"
        )
        stopped_hourly = hard_decimal(
            "GCP_STOPPED_RESOURCE_HOURLY_COST_KRW", "45.725095000"
        )
        expected_budget = hard_decimal(
            "GCP_HARD_STOP_EXPECTED_BUDGET_KRW", "370000"
        )
        guard_interval = float(values.get("GCP_COST_GUARD_INTERVAL_SECONDS", "10"))
        guard_retry = float(values.get("GCP_HARD_STOP_RETRY_SECONDS", "300"))
        vm_cost_start = hard_datetime("GCP_VM_COST_START")
        rate_transition_at = hard_datetime("GCP_RUNTIME_RATE_TRANSITION_AT")
        high_rate_hourly = hard_decimal(
            "GCP_HIGH_RATE_HOURLY_COST_KRW", str(hourly)
        )
        stop_at = hard_datetime("GCP_HARD_STOP_AT")
        expected_budget_id = hard_required("GCP_HARD_STOP_BUDGET_ID")
        billing_account_id = hard_required("GCP_HARD_STOP_BILLING_ACCOUNT_ID")
        arm_id = hard_required("GCP_HARD_STOP_ARM_ID")
        instance_zone = hard_required("GCP_INSTANCE_ZONE")
        instance_name = hard_required("GCP_INSTANCE_NAME")
        budget_display_name = values.get(
            "GCP_HARD_STOP_BUDGET_DISPLAY_NAME",
            "GoLe production credit guard",
        ).strip()

        if hard_stop_enabled:
            if not project_id:
                raise ConfigurationError(
                    "GCP_PROJECT_ID is required when hard stop is enabled"
                )
            if not (
                Decimal("0") < all_in_warning < all_in_danger < all_in_limit
            ):
                raise ConfigurationError(
                    "all-in warning, danger, and stop values must be increasing"
                )
            if not (
                Decimal("0")
                < network_warning_gib
                < network_danger_gib
                < network_limit_gib
            ):
                raise ConfigurationError(
                    "network warning, danger, and stop values must be increasing"
                )
            if not (
                Decimal("0") < runtime_warning < runtime_danger < runtime_limit
            ):
                raise ConfigurationError(
                    "runtime warning, danger, and stop values must be increasing"
                )
            if billing_limit <= 0 or billing_limit >= credit:
                raise ConfigurationError(
                    "billing hard-stop cost must be positive and lower than credit"
                )
            if credit - billing_limit < minimum_reserve:
                raise ConfigurationError(
                    "billing hard-stop cost does not leave the minimum reserve"
                )
            full_horizon_hours = Decimal(
                str(max(0.0, (deadline - vm_cost_start).total_seconds() / 3600))
            )
            snapshot_tail_pre_tax = (
                snapshot_hourly * snapshot_retention_hours
                + manual_snapshot_hourly * full_horizon_hours
            )
            snapshot_tail_gross = snapshot_tail_pre_tax * (Decimal("1") + vat_rate)
            if credit - all_in_limit < snapshot_tail_gross:
                raise ConfigurationError(
                    "all-in hard-stop cost does not reserve the maximum snapshot retention tail"
                )
            if expected_budget <= 0 or vat_rate < 0 or vat_rate > 1:
                raise ConfigurationError("budget and VAT settings are invalid")
            if egress_rate < 0 or stopped_hourly < 0 or high_rate_hourly < hourly:
                raise ConfigurationError("guard hourly costs cannot be negative")
            if guard_interval <= 0 or guard_retry <= 0:
                raise ConfigurationError("guard intervals must be positive")
            if not budget_display_name:
                raise ConfigurationError(
                    "GCP_HARD_STOP_BUDGET_DISPLAY_NAME cannot be empty"
                )
            if (
                vm_cost_start >= rate_transition_at
                or rate_transition_at >= stop_at
                or stop_at > deadline
            ):
                raise ConfigurationError(
                    "VM cost start, hard stop, and credit deadline must be ordered"
                )
            if period_start > vm_cost_start.date():
                raise ConfigurationError(
                    "budget period cannot start after VM cost accounting"
                )

        hard_stop = HardStopPolicy(
            enabled=hard_stop_enabled,
            dry_run=hard_stop_dry_run,
            billing_cost_limit_krw=billing_limit,
            all_in_cost_limit_krw=all_in_limit,
            all_in_warning_krw=all_in_warning,
            all_in_danger_krw=all_in_danger,
            network_limit_bytes=int(network_limit_gib * GIB),
            network_warning_bytes=int(network_warning_gib * GIB),
            network_danger_bytes=int(network_danger_gib * GIB),
            max_runtime_hours=runtime_limit,
            runtime_warning_hours=runtime_warning,
            runtime_danger_hours=runtime_danger,
            minimum_reserve_krw=minimum_reserve,
            expected_budget_amount_krw=expected_budget,
            expected_budget_id=expected_budget_id,
            billing_account_id=billing_account_id,
            budget_display_name=budget_display_name,
            period_start=period_start,
            vm_cost_start=vm_cost_start,
            runtime_rate_transition_at=rate_transition_at,
            high_rate_hourly_cost_krw=high_rate_hourly,
            stop_at=stop_at,
            arm_id=arm_id,
            project_id=project_id or "",
            zone=instance_zone,
            instance_name=instance_name,
            vat_rate=vat_rate,
            network_egress_krw_per_gib=egress_rate,
            stopped_resource_hourly_cost_krw=stopped_hourly,
            tx_bytes_path=Path(
                values.get("GCP_NETWORK_TX_BYTES_PATH", "/host-metrics/tx_bytes")
            ),
            boot_id_path=Path(
                values.get("GCP_HOST_BOOT_ID_PATH", "/host-metrics/boot_id")
            ),
            check_interval_seconds=guard_interval,
            retry_seconds=guard_retry,
        )

        raw_thresholds = values.get(
            "GCP_BUDGET_WARNING_THRESHOLDS", "0.50,0.75,0.90,1.00"
        )
        try:
            thresholds = tuple(
                sorted({_decimal(item.strip(), "warning threshold") for item in raw_thresholds.split(",") if item.strip()})
            )
        except ValueError as exc:
            raise ConfigurationError(str(exc)) from exc
        if not thresholds or thresholds[0] <= 0:
            raise ConfigurationError("warning thresholds must be positive")

        max_messages = int(values.get("PUBSUB_PULL_MAX_MESSAGES", "10"))
        pull_interval = float(values.get("PUBSUB_PULL_INTERVAL_SECONDS", "15"))
        # Keep every synchronous remote request shorter than the local guard
        # cadence so an unavailable Pub/Sub endpoint cannot starve cost checks.
        timeout = float(values.get("BUDGET_HTTP_TIMEOUT_SECONDS", "5"))
        if not 1 <= max_messages <= 100:
            raise ConfigurationError("PUBSUB_PULL_MAX_MESSAGES must be 1..100")
        if pull_interval < 0 or timeout <= 0:
            raise ConfigurationError("poll interval must be non-negative and timeout positive")

        return cls(
            subscription=subscription,
            webhook_url=webhook_url,
            credit_amount_krw=credit,
            credit_deadline=deadline,
            fixed_hourly_cost_krw=hourly,
            snapshot_max_hourly_cost_krw=snapshot_hourly,
            snapshot_retention_hours=snapshot_retention_hours,
            manual_snapshot_hourly_cost_krw=manual_snapshot_hourly,
            warning_thresholds=thresholds,
            state_dir=Path(values.get("BUDGET_STATE_DIR", "/state")),
            timezone_offset_hours=offset,
            max_messages=max_messages,
            pull_interval_seconds=pull_interval,
            http_timeout_seconds=timeout,
            project_id=project_id,
            hard_stop=hard_stop,
        )

    @property
    def local_timezone(self) -> timezone:
        return timezone(timedelta(hours=self.timezone_offset_hours))


@dataclass(frozen=True)
class NotificationPlan:
    should_send: bool
    out_of_order: bool
    daily_key: str
    threshold_events: tuple[str, ...]
    observed_level: Decimal


class StateStore:
    """Small atomically-written JSON state store for at-least-once delivery."""

    def __init__(self, state_dir: Path, max_seen: int = 5000) -> None:
        self.state_dir = state_dir
        self.path = state_dir / "budget-relay-state.json"
        self.max_seen = max_seen
        self._lock = threading.RLock()
        self.load_error: str | None = None
        self.data: dict[str, Any] = {
            "version": STATE_VERSION,
            "seen_message_ids": [],
            "periods": {},
            "cost_guards": {},
        }
        self._load()

    @_synchronized
    def _load(self) -> None:
        if not self.path.exists():
            return
        try:
            loaded = json.loads(self.path.read_text(encoding="utf-8"))
            if loaded.get("version") != STATE_VERSION:
                raise ValueError("unsupported state version")
            if not isinstance(loaded.get("seen_message_ids"), list) or not isinstance(
                loaded.get("periods"), dict
            ):
                raise ValueError("invalid state shape")
            if "cost_guards" in loaded and not isinstance(
                loaded["cost_guards"], dict
            ):
                raise ValueError("invalid cost guard state")
            loaded.setdefault("cost_guards", {})
            self.data = loaded
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            # A corrupt/unreadable state must arm a fail-closed VM stop instead
            # of crash-looping the container while the instance keeps spending.
            self.load_error = f"cannot load budget relay state: {type(exc).__name__}"
            LOG.critical("%s", self.load_error)

    @_synchronized
    def is_seen(self, message_id: str) -> bool:
        return message_id in self.data["seen_message_ids"]

    def _remember_message(self, message_id: str) -> None:
        seen: list[str] = self.data["seen_message_ids"]
        if message_id not in seen:
            seen.append(message_id)
            del seen[:-self.max_seen]

    @_synchronized
    def mark_seen(self, message_id: str) -> None:
        self._remember_message(message_id)
        self._save()

    @_synchronized
    def latest_cost(self, period_start: date, currency_code: str = "KRW") -> Decimal:
        latest_time: datetime | None = None
        latest_cost = Decimal("0")
        for period in self.data["periods"].values():
            try:
                interval = _parse_rfc3339(period["interval_start"], "interval_start")
                published = _parse_rfc3339(
                    period["latest_publish_time"], "latest_publish_time"
                )
                cost = _decimal(period["latest_cost_amount"], "latest_cost_amount")
            except (KeyError, TypeError, ValueError):
                continue
            if interval.date() != period_start:
                continue
            if period.get("currency_code") != currency_code:
                continue
            if latest_time is None or published > latest_time:
                latest_time = published
                latest_cost = cost
        return latest_cost

    def _guard(self, arm_id: str) -> dict[str, Any]:
        guards: dict[str, Any] = self.data.setdefault("cost_guards", {})
        return guards.setdefault(
            arm_id,
            {
                "announced": False,
                "events": [],
                "meter": {
                    "boot_id": None,
                    "last_tx_bytes": None,
                    "cumulative_tx_bytes": 0,
                },
                "tripped": None,
                "last_stop_attempt": None,
                "stop_attempts": 0,
                "stop_accepted": False,
            },
        )

    @_synchronized
    def update_network_meter(
        self, arm_id: str, boot_id: str, current_tx_bytes: int
    ) -> int:
        if current_tx_bytes < 0:
            raise ValueError("network byte counter cannot be negative")
        meter = self._guard(arm_id).setdefault("meter", {})
        previous_boot = meter.get("boot_id")
        previous_tx = meter.get("last_tx_bytes")
        cumulative = int(meter.get("cumulative_tx_bytes", 0))
        if previous_tx is None:
            # The host counter starts at boot, so the first sample also covers
            # traffic sent before the guard container was deployed.
            cumulative += current_tx_bytes
        else:
            previous_tx = int(previous_tx)
            if previous_boot == boot_id and current_tx_bytes >= previous_tx:
                cumulative += current_tx_bytes - previous_tx
            elif previous_boot != boot_id:
                # A reboot reset the host NIC counter. Count all bytes observed in
                # the new boot; the persistent Docker volume retains older boots.
                cumulative += current_tx_bytes
        meter.update(
            {
                "boot_id": boot_id,
                "last_tx_bytes": current_tx_bytes,
                "cumulative_tx_bytes": cumulative,
            }
        )
        self._save()
        return cumulative

    @_synchronized
    def guard_announced(self, arm_id: str) -> bool:
        return bool(self._guard(arm_id).get("announced", False))

    @_synchronized
    def mark_guard_announced(self, arm_id: str) -> None:
        self._guard(arm_id)["announced"] = True
        self._save()

    @_synchronized
    def unseen_guard_events(self, arm_id: str, events: Iterable[str]) -> tuple[str, ...]:
        seen = set(self._guard(arm_id).get("events", []))
        return tuple(event for event in events if event not in seen)

    @_synchronized
    def mark_guard_events(self, arm_id: str, events: Iterable[str]) -> None:
        guard = self._guard(arm_id)
        notified = set(guard.get("events", []))
        notified.update(events)
        guard["events"] = sorted(notified)
        self._save()

    @_synchronized
    def trip_guard(self, arm_id: str, reason: str, now: datetime) -> None:
        guard = self._guard(arm_id)
        if guard.get("tripped") is None:
            guard["tripped"] = {
                "reason": reason,
                "at": _format_rfc3339(now),
            }
            self._save()

    @_synchronized
    def guard_trip_reason(self, arm_id: str) -> str | None:
        tripped = self._guard(arm_id).get("tripped")
        return str(tripped.get("reason")) if isinstance(tripped, dict) else None

    @_synchronized
    def guard_network_bytes(self, arm_id: str) -> int:
        meter = self._guard(arm_id).get("meter", {})
        try:
            return max(0, int(meter.get("cumulative_tx_bytes", 0)))
        except (TypeError, ValueError):
            return 0

    @_synchronized
    def stop_attempt_due(
        self, arm_id: str, now: datetime, retry_seconds: float
    ) -> bool:
        raw = self._guard(arm_id).get("last_stop_attempt")
        if not raw:
            return True
        last = _parse_rfc3339(str(raw), "last_stop_attempt")
        return (now.astimezone(timezone.utc) - last).total_seconds() >= retry_seconds

    @_synchronized
    def record_stop_attempt(self, arm_id: str, now: datetime) -> int:
        guard = self._guard(arm_id)
        guard["last_stop_attempt"] = _format_rfc3339(now)
        attempt = int(guard.get("stop_attempts", 0)) + 1
        guard["stop_attempts"] = attempt
        self._save()
        return attempt

    @_synchronized
    def mark_stop_accepted(self, arm_id: str) -> None:
        self._guard(arm_id)["stop_accepted"] = True
        self._save()

    @_synchronized
    def plan(
        self,
        message_id: str,
        publish_time: datetime,
        budget: BudgetNotification,
        thresholds: Iterable[Decimal],
        now: datetime,
    ) -> NotificationPlan:
        if self.is_seen(message_id):
            return NotificationPlan(False, False, now.date().isoformat(), (), Decimal("0"))

        period = self.data["periods"].get(budget.period_key, {})
        latest_text = period.get("latest_publish_time")
        latest = _parse_rfc3339(latest_text, "latest_publish_time") if latest_text else None
        out_of_order = latest is not None and publish_time < latest
        daily_key = now.date().isoformat()
        notified = set(period.get("threshold_events", []))

        actual_signal = max(
            budget.actual_ratio,
            budget.alert_threshold or Decimal("0"),
        )
        forecast_signal = budget.forecast_threshold or Decimal("0")
        threshold_events: list[str] = []
        if not out_of_order:
            for threshold in thresholds:
                token = _threshold_token("actual", threshold)
                if actual_signal >= threshold and token not in notified:
                    threshold_events.append(token)
                forecast_token = _threshold_token("forecast", threshold)
                if forecast_signal >= threshold and forecast_token not in notified:
                    threshold_events.append(forecast_token)

        daily_due = not out_of_order and daily_key not in period.get("daily_dates", [])
        observed = max(actual_signal, forecast_signal)
        return NotificationPlan(
            should_send=daily_due or bool(threshold_events),
            out_of_order=out_of_order,
            daily_key=daily_key,
            threshold_events=tuple(threshold_events),
            observed_level=observed,
        )

    @_synchronized
    def commit(
        self,
        message_id: str,
        publish_time: datetime,
        budget: BudgetNotification,
        plan: NotificationPlan,
    ) -> None:
        self._remember_message(message_id)

        periods: dict[str, Any] = self.data["periods"]
        period = periods.setdefault(
            budget.period_key,
            {
                "display_name": budget.display_name,
                "interval_start": _format_rfc3339(budget.interval_start),
                "currency_code": budget.currency_code,
                "latest_publish_time": None,
                "latest_cost_amount": "0",
                "threshold_events": [],
                "daily_dates": [],
            },
        )
        latest_text = period.get("latest_publish_time")
        latest = _parse_rfc3339(latest_text, "latest_publish_time") if latest_text else None
        if latest is None or publish_time >= latest:
            period["latest_publish_time"] = _format_rfc3339(publish_time)
            period["latest_cost_amount"] = str(budget.cost_amount)

        if plan.should_send:
            current_events = set(period.get("threshold_events", []))
            current_events.update(plan.threshold_events)
            period["threshold_events"] = sorted(current_events)
            daily_dates = period.setdefault("daily_dates", [])
            if plan.daily_key not in daily_dates:
                daily_dates.append(plan.daily_key)
            del daily_dates[:-90]

        # Keep a bounded number of old billing periods.
        if len(periods) > 24:
            ordered = sorted(
                periods.items(), key=lambda item: item[1].get("interval_start", "")
            )
            for key, _ in ordered[:-24]:
                del periods[key]

        self._save()

    @_synchronized
    def _save(self) -> None:
        temporary: str | None = None
        try:
            self.state_dir.mkdir(parents=True, exist_ok=True)
            fd, temporary = tempfile.mkstemp(
                prefix=".budget-relay-", suffix=".json", dir=self.state_dir
            )
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(self.data, handle, ensure_ascii=False, sort_keys=True)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, self.path)
        except OSError as exc:
            self.load_error = f"cannot persist budget relay state: {type(exc).__name__}"
            raise
        finally:
            if temporary is not None:
                try:
                    os.unlink(temporary)
                except FileNotFoundError:
                    pass


def _threshold_token(kind: str, threshold: Decimal) -> str:
    return f"{kind}:{format(threshold.normalize(), 'f')}"


def _money(amount: Decimal, currency: str) -> str:
    quantized = amount.quantize(Decimal("1")) if currency == "KRW" else amount.quantize(Decimal("0.01"))
    prefix = "₩" if currency == "KRW" else f"{currency} "
    sign = "-" if quantized < 0 else ""
    return f"{sign}{prefix}{abs(quantized):,.0f}" if currency == "KRW" else f"{sign}{prefix}{abs(quantized):,.2f}"


def _percentage(value: Decimal) -> str:
    return f"{value * 100:.1f}%"


@dataclass(frozen=True)
class CostGuardSnapshot:
    observed_billing_gross: Decimal
    modeled_fixed_pre_tax: Decimal
    network_bytes: int
    modeled_network_pre_tax: Decimal
    estimated_current_gross: Decimal
    stopped_resources_remaining_pre_tax: Decimal
    all_in_if_stopped_gross: Decimal
    projected_running_gross: Decimal
    runtime_hours: Decimal
    remaining_hours: Decimal
    warning_events: tuple[str, ...]
    stop_reason: str | None

    @property
    def network_gib(self) -> Decimal:
        return Decimal(self.network_bytes) / GIB


def calculate_cost_guard_snapshot(
    config: Config,
    now: datetime,
    observed_billing_gross: Decimal,
    network_bytes: int,
) -> CostGuardSnapshot:
    policy = config.hard_stop
    if policy is None:
        raise ConfigurationError("hard-stop policy is missing")
    now_local = now.astimezone(config.local_timezone)
    runtime_seconds = max(
        0.0, (now_local - policy.vm_cost_start).total_seconds()
    )
    runtime_hours = Decimal(str(runtime_seconds / 3600))
    remaining_seconds = max(
        0.0, (config.credit_deadline - now_local).total_seconds()
    )
    remaining_hours = Decimal(str(remaining_seconds / 3600))
    # Model the measured 4-vCPU rate only through the reviewed resize deadline.
    # After that instant the root broker independently attests e2-standard-2 or
    # powers off. Snapshot rates assume three full scheduled points plus the
    # retained manual pre-IaC point from the first runtime hour.
    high_rate_hours = min(
        runtime_hours,
        Decimal(
            str(
                (
                    policy.runtime_rate_transition_at - policy.vm_cost_start
                ).total_seconds()
                / 3600
            )
        ),
    )
    modeled_fixed = (
        policy.high_rate_hourly_cost_krw * high_rate_hours
        + config.fixed_hourly_cost_krw
        * max(Decimal("0"), runtime_hours - high_rate_hours)
        + (
            config.snapshot_max_hourly_cost_krw
            + config.manual_snapshot_hourly_cost_krw
        )
        * runtime_hours
    )
    modeled_network = (
        Decimal(network_bytes) / GIB * policy.network_egress_krw_per_gib
    )
    vat_multiplier = Decimal("1") + policy.vat_rate
    modeled_current_gross = (modeled_fixed + modeled_network) * vat_multiplier
    # Budget costAmount can already include posted taxes. Compare it with the
    # VAT-inclusive local model instead of taxing the Billing value twice.
    estimated_current_gross = max(
        observed_billing_gross, modeled_current_gross
    )
    # Cover the three-point scheduled chain through 72h retention and the manual
    # pre-IaC snapshot independently through the credit deadline.
    retained_chain_tail = (
        config.snapshot_max_hourly_cost_krw
        * min(remaining_hours, config.snapshot_retention_hours)
    )
    final_snapshot_tail = (
        config.manual_snapshot_hourly_cost_krw * remaining_hours
    )
    snapshot_tail = retained_chain_tail + final_snapshot_tail
    stopped_remaining = (
        policy.stopped_resource_hourly_cost_krw * remaining_hours
        + snapshot_tail
    )
    all_in_if_stopped = estimated_current_gross + (
        stopped_remaining * vat_multiplier
    )
    projected_running = (
        estimated_current_gross
        + (
            config.fixed_hourly_cost_krw
            + config.snapshot_max_hourly_cost_krw
            + config.manual_snapshot_hourly_cost_krw
        )
        * remaining_hours
        * vat_multiplier
    )

    warnings: list[str] = []
    if all_in_if_stopped >= policy.all_in_warning_krw:
        warnings.append("all-in-warning")
    if all_in_if_stopped >= policy.all_in_danger_krw:
        warnings.append("all-in-danger")
    if network_bytes >= policy.network_warning_bytes:
        warnings.append("network-warning")
    if network_bytes >= policy.network_danger_bytes:
        warnings.append("network-danger")
    if runtime_hours >= policy.runtime_warning_hours:
        warnings.append("runtime-warning")
    if runtime_hours >= policy.runtime_danger_hours:
        warnings.append("runtime-danger")

    stop_reason: str | None = None
    if now_local >= policy.stop_at:
        stop_reason = "absolute-cutoff"
    elif runtime_hours >= policy.max_runtime_hours:
        stop_reason = "runtime-limit"
    elif network_bytes >= policy.network_limit_bytes:
        stop_reason = "network-limit"
    elif observed_billing_gross >= policy.billing_cost_limit_krw:
        stop_reason = "billing-limit"
    elif all_in_if_stopped >= policy.all_in_cost_limit_krw:
        stop_reason = "all-in-limit"

    return CostGuardSnapshot(
        observed_billing_gross=observed_billing_gross,
        modeled_fixed_pre_tax=modeled_fixed,
        network_bytes=network_bytes,
        modeled_network_pre_tax=modeled_network,
        estimated_current_gross=estimated_current_gross,
        stopped_resources_remaining_pre_tax=stopped_remaining,
        all_in_if_stopped_gross=all_in_if_stopped,
        projected_running_gross=projected_running,
        runtime_hours=runtime_hours,
        remaining_hours=remaining_hours,
        warning_events=tuple(warnings),
        stop_reason=stop_reason,
    )


def render_cost_guard_message(
    kind: str,
    snapshot: CostGuardSnapshot,
    config: Config,
    reason: str | None = None,
) -> str:
    policy = config.hard_stop
    if policy is None:
        raise ConfigurationError("hard-stop policy is missing")
    reason_names = {
        "absolute-cutoff": "크레딧 이전 절대 종료 시각 도달",
        "runtime-limit": "최대 가동시간 도달",
        "network-limit": "송신 트래픽 상한 도달",
        "billing-limit": "Cloud Billing 실제비용 정지선 도달",
        "all-in-limit": "VAT·정지 후 비용 포함 상한 도달",
        "meter-unavailable": "호스트 비용 계측기 확인 실패",
        "state-unavailable": "비용 가드 상태 저장소 확인 실패",
        "previous-trip": "이전 비용 가드 정지 잠금 유지",
    }
    if kind == "activation":
        heading = "🛡️ **GoLe GCP 실시간 비용 가드 활성화**"
    elif kind == "warning":
        heading = "⚠️ **GoLe GCP 로컬 비용 가드 경고**"
    else:
        mode = "드라이런" if policy.dry_run else "자동 정지"
        heading = f"🛑 **GoLe GCP 비용 가드 · {mode}**"

    lines = [
        heading,
        f"- 로컬 고정비 추정: {_money(snapshot.modeled_fixed_pre_tax, 'KRW')}",
        f"- 호스트 송신량: {snapshot.network_gib:.2f} GiB / {Decimal(policy.network_limit_bytes) / GIB:.0f} GiB",
        f"- 송신비 보수 추정: {_money(snapshot.modeled_network_pre_tax, 'KRW')}",
        f"- 최신 Billing 비용(세금 게시분 포함): {_money(snapshot.observed_billing_gross, 'KRW')}",
        f"- 지금 정지할 때 만료까지 총액(VAT 포함): **{_money(snapshot.all_in_if_stopped_gross, 'KRW')}**",
        f"- 계속 운영 예상액(VAT 포함): {_money(snapshot.projected_running_gross, 'KRW')}",
        f"- 가동시간: {snapshot.runtime_hours:.1f} / {policy.max_runtime_hours:.0f}시간",
        f"- 자동 정지선: Billing {_money(policy.billing_cost_limit_krw, 'KRW')} · all-in {_money(policy.all_in_cost_limit_krw, 'KRW')}",
        f"- 절대 종료: {policy.stop_at.isoformat()}",
        f"- 가드 ID: `{policy.arm_id}`",
    ]
    if reason:
        lines.insert(1, f"- 사유: **{reason_names.get(reason, reason)}**")
    lines.append(
        "_Billing 값은 지연될 수 있어 호스트 시간·송신 바이트 상한 계산을 함께 사용합니다._"
    )
    return "\n".join(lines)[:1990]


def render_discord_message(
    budget: BudgetNotification,
    plan: NotificationPlan,
    config: Config,
    now: datetime,
    guard_snapshot: CostGuardSnapshot | None = None,
) -> str:
    now_local = now.astimezone(config.local_timezone)
    deadline = config.credit_deadline.astimezone(config.local_timezone)
    remaining_hours = Decimal(
        str(max(0.0, (deadline - now_local).total_seconds()) / 3600)
    )
    future_fixed = config.fixed_hourly_cost_krw * remaining_hours

    if budget.currency_code == "KRW":
        if guard_snapshot is not None:
            current_cost = guard_snapshot.estimated_current_gross
            projected_total = guard_snapshot.projected_running_gross
            vat_multiplier = Decimal("1") + (
                config.hard_stop.vat_rate if config.hard_stop else Decimal("0")
            )
            future_fixed_display = future_fixed * vat_multiplier
        else:
            current_cost = budget.cost_amount
            projected_total = budget.cost_amount + future_fixed
            future_fixed_display = future_fixed
        current_credit_remaining = config.credit_amount_krw - current_cost
        projected_credit_remaining = config.credit_amount_krw - projected_total
        projection_lines = [
            f"- 설정 크레딧: {_money(config.credit_amount_krw, 'KRW')}",
            f"- 현재 비용 차감 잔액: {_money(current_credit_remaining, 'KRW')}",
            f"- 만료일까지 예상 추가 고정비: {_money(future_fixed_display, 'KRW')}",
            f"- 만료 시점 예상 총비용(VAT 포함): {_money(projected_total, 'KRW')}",
            f"- 예상 크레딧 잔액: {_money(projected_credit_remaining, 'KRW')}",
        ]
        if guard_snapshot is not None:
            projection_lines.extend(
                [
                    f"- 로컬 고정비 추정: {_money(guard_snapshot.modeled_fixed_pre_tax, 'KRW')}",
                    f"- 호스트 송신량: {guard_snapshot.network_gib:.2f} GiB",
                    f"- 지금 정지할 때 총액(VAT 포함): {_money(guard_snapshot.all_in_if_stopped_gross, 'KRW')}",
                ]
            )
        overrun = projected_credit_remaining < 0
    else:
        projection_lines = [
            f"- 설정 크레딧: {_money(config.credit_amount_krw, 'KRW')}",
            f"- 만료일까지 예상 추가 고정비: {_money(future_fixed, 'KRW')}",
            "- 크레딧 잔액 추정: 비용 통화가 KRW가 아니어서 생략",
        ]
        overrun = False

    actual_level = max(
        budget.actual_ratio,
        budget.alert_threshold or Decimal("0"),
    )
    forecast_level = budget.forecast_threshold or Decimal("0")
    if overrun or actual_level >= Decimal("1"):
        icon = "🚨"
    elif actual_level >= Decimal("0.9"):
        icon = "🔴"
    elif actual_level >= Decimal("0.75"):
        icon = "🟠"
    elif actual_level >= Decimal("0.5") or forecast_level >= Decimal("0.5"):
        icon = "🟡"
    else:
        icon = "🟢"

    reasons: list[str] = []
    if any(item.startswith("actual:") for item in plan.threshold_events):
        actual_levels = [Decimal(item.split(":", 1)[1]) for item in plan.threshold_events if item.startswith("actual:")]
        reasons.append(f"실제비용 {_percentage(max(actual_levels))} 임계치")
    if any(item.startswith("forecast:") for item in plan.threshold_events):
        forecast_levels = [Decimal(item.split(":", 1)[1]) for item in plan.threshold_events if item.startswith("forecast:")]
        reasons.append(
            f"Google forecast {_percentage(max(forecast_levels))} 임계치(실제 초과 아님)"
        )
    if not reasons:
        reasons.append("일일 현황")

    d_day = (deadline.date() - now_local.date()).days
    budget_remaining = budget.budget_amount - budget.cost_amount
    lines = [
        f"{icon} **GoLe GCP 예산 알림 · {' / '.join(reasons)}**",
        f"- Google Billing 게시 실제 누적비용: **{_money(budget.cost_amount, budget.currency_code)} / {_money(budget.budget_amount, budget.currency_code)}** ({_percentage(budget.actual_ratio)})",
        f"- Google Budget 한도까지 실제 잔액: **{_money(budget_remaining, budget.currency_code)}**",
        *projection_lines,
        f"- 고정비 가정: {_money(config.fixed_hourly_cost_krw, 'KRW')}/시간",
        f"- 크레딧 만료: {deadline.date().isoformat()} (D-{max(d_day, 0)})",
        f"- 비용 구간 시작: {budget.interval_start.date().isoformat()}",
        "_Google forecast 알림, 게시된 실제비용, 로컬 보수 projection은 서로 다른 신호이며 forecast만으로 초과나 자동 정지를 뜻하지 않습니다._",
    ]
    if overrun:
        lines.insert(
            1,
            "**로컬 보수 projection상 현재 사양을 유지하면 설정 크레딧을 초과할 것으로 예상됩니다(게시 실제비용 초과와 다름).**",
        )
    return "\n".join(lines)[:1990]


class MetadataCredentials:
    def __init__(self, timeout: float) -> None:
        self.timeout = timeout
        self._token = ""
        self._expires_at = 0.0

    def _get(self, path: str) -> bytes:
        request = urllib.request.Request(
            f"{METADATA_ROOT}/{path}", headers={"Metadata-Flavor": "Google"}
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return response.read()
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
            raise RemoteServiceError(
                f"metadata request failed ({type(exc).__name__})"
            ) from exc

    def project_id(self) -> str:
        return self._get("project/project-id").decode("utf-8").strip()

    def access_token(self, force_refresh: bool = False) -> str:
        if not force_refresh and self._token and time.monotonic() < self._expires_at:
            return self._token
        try:
            response = json.loads(
                self._get("instance/service-accounts/default/token")
            )
            token = response["access_token"]
            expires_in = max(1, int(response.get("expires_in", 300)))
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise RemoteServiceError("metadata returned an invalid OAuth token") from exc
        self._token = token
        self._expires_at = time.monotonic() + max(1, expires_in - 60)
        return token


class PubSubClient:
    def __init__(
        self,
        subscription: str,
        project_id: str | None,
        credentials: MetadataCredentials,
        timeout: float,
    ) -> None:
        if subscription.startswith("projects/"):
            parts = subscription.split("/")
            if len(parts) != 4 or parts[2] != "subscriptions":
                raise ConfigurationError("invalid full Pub/Sub subscription name")
            self.subscription = subscription
        else:
            resolved_project = project_id or credentials.project_id()
            self.subscription = f"projects/{resolved_project}/subscriptions/{subscription}"
        self.credentials = credentials
        self.timeout = timeout

    def _post(self, action: str, payload: dict[str, Any]) -> dict[str, Any]:
        endpoint = f"{PUBSUB_ROOT}/{urllib.parse.quote(self.subscription, safe='/')}:{action}"
        for attempt in range(2):
            token = self.credentials.access_token(force_refresh=attempt > 0)
            request = urllib.request.Request(
                endpoint,
                data=json.dumps(payload).encode("utf-8"),
                method="POST",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Type": "application/json",
                },
            )
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    body = response.read()
                    return json.loads(body) if body else {}
            except urllib.error.HTTPError as exc:
                if exc.code == 401 and attempt == 0:
                    continue
                raise RemoteServiceError(f"Pub/Sub {action} failed (HTTP {exc.code})") from exc
            except (urllib.error.URLError, TimeoutError) as exc:
                reason = getattr(exc, "reason", None)
                if action == "pull" and (
                    isinstance(exc, TimeoutError)
                    or isinstance(reason, TimeoutError)
                ):
                    raise PullIdleTimeout(
                        "Pub/Sub pull reached its idle deadline"
                    ) from exc
                raise RemoteServiceError(
                    f"Pub/Sub {action} failed ({type(exc).__name__})"
                ) from exc
            except json.JSONDecodeError as exc:
                raise RemoteServiceError(f"Pub/Sub {action} returned invalid JSON") from exc
        raise RemoteServiceError(f"Pub/Sub {action} authorization failed")

    def pull(self, max_messages: int) -> list[dict[str, Any]]:
        try:
            response = self._post("pull", {"maxMessages": max_messages})
        except PullIdleTimeout:
            # Unary Pull may wait for a bounded period when the backlog is
            # empty. Our shorter HTTP deadline is an ordinary idle poll, not a
            # relay outage; the independent cost-guard thread keeps running.
            return []
        messages = response.get("receivedMessages", [])
        if not isinstance(messages, list):
            raise RemoteServiceError("Pub/Sub pull returned an invalid message list")
        return messages

    def acknowledge(self, ack_ids: list[str]) -> None:
        if ack_ids:
            self._post("acknowledge", {"ackIds": ack_ids})


class DiscordClient:
    def __init__(self, webhook_url: str, timeout: float) -> None:
        self._webhook_url = webhook_url
        self.timeout = timeout

    def send(self, content: str) -> None:
        if not self._webhook_url:
            raise RemoteServiceError("Discord webhook is not configured")
        request = urllib.request.Request(
            self._webhook_url,
            data=json.dumps(
                {"content": content, "username": "GoLe GCP Budget"},
                ensure_ascii=False,
            ).encode("utf-8"),
            method="POST",
            headers={
                "Content-Type": "application/json",
                # Discord/Cloudflare는 Python urllib의 기본 User-Agent를 403으로
                # 거부할 수 있으므로 운영 릴레이를 명시적으로 식별한다.
                "User-Agent": "GoLe-Budget-Relay/1.0",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                if not 200 <= response.status < 300:
                    raise RemoteServiceError(
                        f"Discord delivery failed (HTTP {response.status})"
                    )
        except urllib.error.HTTPError as exc:
            raise RemoteServiceError(
                f"Discord delivery failed (HTTP {exc.code})"
            ) from exc
        except (urllib.error.URLError, TimeoutError) as exc:
            raise RemoteServiceError(
                f"Discord delivery failed ({type(exc).__name__})"
            ) from exc


class ComputeClient:
    def __init__(
        self,
        project_id: str,
        zone: str,
        instance_name: str,
        credentials: MetadataCredentials,
        timeout: float,
    ) -> None:
        self.project_id = project_id
        self.zone = zone
        self.instance_name = instance_name
        self.credentials = credentials
        self.timeout = timeout

    def stop(self, request_id: str) -> None:
        query = urllib.parse.urlencode({"requestId": request_id})
        endpoint = (
            f"{COMPUTE_ROOT}/projects/{urllib.parse.quote(self.project_id, safe='')}"
            f"/zones/{urllib.parse.quote(self.zone, safe='')}"
            f"/instances/{urllib.parse.quote(self.instance_name, safe='')}/stop?{query}"
        )
        for attempt in range(2):
            token = self.credentials.access_token(force_refresh=attempt > 0)
            request = urllib.request.Request(
                endpoint,
                data=b"",
                method="POST",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Content-Length": "0",
                    "User-Agent": "GoLe-Cost-Guard/1.0",
                },
            )
            try:
                with urllib.request.urlopen(
                    request, timeout=self.timeout
                ) as response:
                    if not 200 <= response.status < 300:
                        raise RemoteServiceError(
                            f"Compute stop failed (HTTP {response.status})"
                        )
                    return
            except urllib.error.HTTPError as exc:
                if exc.code == 401 and attempt == 0:
                    continue
                raise RemoteServiceError(
                    f"Compute stop failed (HTTP {exc.code})"
                ) from exc
            except (urllib.error.URLError, TimeoutError) as exc:
                raise RemoteServiceError(
                    f"Compute stop failed ({type(exc).__name__})"
                ) from exc
        raise RemoteServiceError("Compute stop authorization failed")


class CloudBrokerClient:
    """Narrow Unix client; it cannot select cloud resources or receive tokens."""

    def __init__(self, socket_path: str, timeout: float) -> None:
        path = Path(socket_path)
        if not path.is_absolute() or str(path) != "/run/gole-cloud-broker/broker.sock":
            raise ConfigurationError("GOLE_CLOUD_BROKER_SOCKET is invalid")
        self.socket_path = str(path)
        self.timeout = timeout

    def request(self, payload: dict[str, Any]) -> dict[str, Any]:
        encoded = json.dumps(payload, separators=(",", ":")).encode("utf-8") + b"\n"
        if len(encoded) > 131072:
            raise RemoteServiceError("cloud broker request is too large")
        try:
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
                connection.settimeout(self.timeout)
                connection.connect(self.socket_path)
                connection.sendall(encoded)
                response = b""
                while not response.endswith(b"\n"):
                    chunk = connection.recv(65536)
                    if not chunk:
                        break
                    response += chunk
                    if len(response) > 4 * 1024 * 1024:
                        raise RemoteServiceError("cloud broker response is too large")
            decoded = json.loads(response)
            if not isinstance(decoded, dict) or decoded.get("ok") is not True:
                raise RemoteServiceError("cloud broker rejected the fixed operation")
            result = decoded.get("result")
            if not isinstance(result, dict):
                raise RemoteServiceError("cloud broker returned an invalid response")
            return result
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            if isinstance(exc, RemoteServiceError):
                raise
            raise RemoteServiceError(
                f"cloud broker request failed ({type(exc).__name__})"
            ) from exc


class BrokerPubSubClient:
    def __init__(self, broker: CloudBrokerClient) -> None:
        self.broker = broker

    def pull(self, max_messages: int) -> list[dict[str, Any]]:
        result = self.broker.request(
            {"operation": "pull", "max_messages": max_messages}
        )
        messages = result.get("messages", [])
        if not isinstance(messages, list):
            raise RemoteServiceError("cloud broker returned invalid messages")
        return messages

    def acknowledge(self, ack_ids: list[str]) -> None:
        if ack_ids:
            self.broker.request({"operation": "acknowledge", "ack_ids": ack_ids})


class BrokerComputeClient:
    def __init__(self, broker: CloudBrokerClient) -> None:
        self.broker = broker

    def stop(self, request_id: str) -> None:
        self.broker.request({"operation": "stop", "request_id": request_id})


class CostGuard:
    def __init__(
        self,
        config: Config,
        state: StateStore,
        discord: DiscordClient,
        compute: ComputeClient,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        if config.hard_stop is None or not config.hard_stop.enabled:
            raise ConfigurationError("cost guard requires an enabled hard-stop policy")
        self.config = config
        self.policy = config.hard_stop
        self.state = state
        self.discord = discord
        self.compute = compute
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self._check_lock = threading.RLock()
        self._last_discord_warning = 0.0

    def _read_host_counter(self) -> tuple[str, int]:
        try:
            boot_id = self.policy.boot_id_path.read_text(encoding="utf-8").strip()
            tx_text = self.policy.tx_bytes_path.read_text(encoding="utf-8").strip()
            tx_bytes = int(tx_text)
        except (OSError, ValueError) as exc:
            raise RuntimeError(
                f"cannot read host cost meter ({type(exc).__name__})"
            ) from exc
        if not boot_id or tx_bytes < 0:
            raise RuntimeError("host cost meter returned invalid data")
        return boot_id, tx_bytes

    def snapshot(
        self,
        now: datetime | None = None,
        billing_override: Decimal | None = None,
    ) -> CostGuardSnapshot:
        with self._check_lock:
            sampled_at = (now or self.clock()).astimezone(
                self.config.local_timezone
            )
            boot_id, current_tx = self._read_host_counter()
            cumulative_tx = self.state.update_network_meter(
                self.policy.arm_id, boot_id, current_tx
            )
            observed = self.state.latest_cost(self.policy.period_start)
            if billing_override is not None:
                observed = max(observed, billing_override)
            return calculate_cost_guard_snapshot(
                self.config,
                sampled_at,
                observed,
                cumulative_tx,
            )

    def _notify_best_effort(self, content: str) -> bool:
        try:
            self.discord.send(content)
            return True
        except RemoteServiceError as exc:
            now_monotonic = time.monotonic()
            if now_monotonic - self._last_discord_warning >= 3600:
                LOG.warning("비용 가드 Discord 전송 실패: %s", exc)
                self._last_discord_warning = now_monotonic
            return False

    def _enforce_stop(
        self,
        snapshot: CostGuardSnapshot,
        reason: str,
        now: datetime,
    ) -> None:
        if not self.policy.dry_run:
            # Persist the lock before any network request. A manual restart of a
            # tripped VM therefore cannot silently resume spending.
            try:
                self.state.trip_guard(self.policy.arm_id, reason, now)
            except (OSError, RuntimeError, ValueError) as exc:
                # Persistence is desirable for restart locking, but losing the
                # state volume must never prevent the immediate Compute stop.
                LOG.critical(
                    "비용 가드 정지 잠금 저장 실패, VM 정지를 계속합니다 (%s)",
                    type(exc).__name__,
                )
        event = f"stop-notified:{reason}"

        if self.policy.dry_run:
            try:
                event_unseen = event in self.state.unseen_guard_events(
                    self.policy.arm_id, [event]
                )
            except (OSError, RuntimeError, ValueError):
                event_unseen = True
            if event_unseen:
                if self._notify_best_effort(
                    render_cost_guard_message("stop", snapshot, self.config, reason)
                ):
                    try:
                        self.state.mark_guard_events(self.policy.arm_id, [event])
                    except (OSError, RuntimeError, ValueError):
                        pass
            dry_event = f"dry-run:{reason}"
            try:
                unseen = self.state.unseen_guard_events(
                    self.policy.arm_id, [dry_event]
                )
            except (OSError, RuntimeError, ValueError):
                unseen = (dry_event,)
            if dry_event in unseen:
                try:
                    self.state.mark_guard_events(self.policy.arm_id, [dry_event])
                except (OSError, RuntimeError, ValueError):
                    pass
                LOG.warning("비용 가드 드라이런 정지 조건 충족: %s", reason)
            return

        stop_error: RemoteServiceError | None = None
        try:
            stop_due = self.state.stop_attempt_due(
                self.policy.arm_id, now, self.policy.retry_seconds
            )
        except (OSError, RuntimeError, ValueError):
            stop_due = True
        if stop_due:
            try:
                stop_attempt = self.state.record_stop_attempt(
                    self.policy.arm_id, now
                )
            except (OSError, RuntimeError, ValueError):
                # A unique persisted sequence is unavailable. The timestamp is
                # still unique enough for a fail-closed retry request ID.
                stop_attempt = int(now.timestamp() * 1_000_000)
            request_id = str(
                uuid.uuid5(
                    uuid.NAMESPACE_URL,
                    ":".join(
                        (
                            "gole-cost-guard",
                            self.policy.project_id,
                            self.policy.zone,
                            self.policy.instance_name,
                            self.policy.arm_id,
                            str(stop_attempt),
                        )
                    ),
                )
            )
            try:
                # Attempt the protective action before any optional webhook
                # delivery. Discord latency or failure cannot hold the VM open.
                self.compute.stop(request_id)
            except RemoteServiceError as exc:
                stop_error = exc
            else:
                try:
                    self.state.mark_stop_accepted(self.policy.arm_id)
                except (OSError, RuntimeError, ValueError):
                    pass
                LOG.critical("비용 가드가 VM 자동 정지 요청을 수락받았습니다")

        try:
            event_unseen = event in self.state.unseen_guard_events(
                self.policy.arm_id, [event]
            )
        except (OSError, RuntimeError, ValueError):
            event_unseen = True
        if event_unseen:
            if self._notify_best_effort(
                render_cost_guard_message("stop", snapshot, self.config, reason)
            ):
                try:
                    self.state.mark_guard_events(self.policy.arm_id, [event])
                except (OSError, RuntimeError, ValueError):
                    pass
        if stop_error is not None:
            raise stop_error

    def check(
        self, billing_override: Decimal | None = None
    ) -> CostGuardSnapshot:
        with self._check_lock:
            return self._check(billing_override)

    def _check(
        self, billing_override: Decimal | None = None
    ) -> CostGuardSnapshot:
        now = self.clock().astimezone(self.config.local_timezone)
        persisted_reason = self.state.guard_trip_reason(self.policy.arm_id)
        try:
            snapshot = self.snapshot(now, billing_override=billing_override)
        except (RuntimeError, OSError) as exc:
            # The safety latch and Billing hard stop remain enforceable even if
            # the host metric bind mount disappears. Meter loss itself fails
            # closed because otherwise network spend would become unbounded.
            observed = self.state.latest_cost(self.policy.period_start)
            if billing_override is not None:
                observed = max(observed, billing_override)
            snapshot = calculate_cost_guard_snapshot(
                self.config,
                now,
                observed,
                self.state.guard_network_bytes(self.policy.arm_id),
            )
            LOG.error("호스트 비용 계측기 실패로 VM을 안전 정지합니다: %s", exc)
            if persisted_reason:
                fallback_reason = "previous-trip"
            elif self.state.load_error:
                fallback_reason = "state-unavailable"
            else:
                fallback_reason = "meter-unavailable"
            self._enforce_stop(snapshot, fallback_reason, now)
            return snapshot
        if self.state.load_error:
            self._enforce_stop(snapshot, "state-unavailable", now)
            return snapshot
        reason = persisted_reason or snapshot.stop_reason
        if reason:
            self._enforce_stop(
                snapshot,
                reason if persisted_reason is None else "previous-trip",
                now,
            )
            return snapshot

        if not self.state.guard_announced(self.policy.arm_id):
            if self._notify_best_effort(
                render_cost_guard_message("activation", snapshot, self.config)
            ):
                self.state.mark_guard_announced(self.policy.arm_id)

        events = self.state.unseen_guard_events(
            self.policy.arm_id, snapshot.warning_events
        )
        if events:
            if self._notify_best_effort(
                render_cost_guard_message("warning", snapshot, self.config)
            ):
                self.state.mark_guard_events(self.policy.arm_id, events)
        return snapshot


class Relay:
    def __init__(
        self,
        config: Config,
        pubsub: PubSubClient,
        discord: DiscordClient,
        state: StateStore,
        clock: Callable[[], datetime] | None = None,
        cost_guard: CostGuard | None = None,
    ) -> None:
        self.config = config
        self.pubsub = pubsub
        self.discord = discord
        self.state = state
        self.clock = clock or (lambda: datetime.now(timezone.utc))
        self.cost_guard = cost_guard

    def process(self, received: dict[str, Any]) -> bool:
        """Process one message, returning True only if it was acknowledged."""
        ack_id = received.get("ackId")
        message = received.get("message")
        if not isinstance(ack_id, str) or not isinstance(message, dict):
            LOG.warning("유효하지 않은 Pub/Sub envelope를 건너뜁니다")
            return False

        message_id = str(message.get("messageId", ""))
        if not message_id:
            LOG.warning("messageId가 없는 Pub/Sub 메시지를 ACK 처리합니다")
            self.pubsub.acknowledge([ack_id])
            return True
        if self.state.is_seen(message_id):
            self.pubsub.acknowledge([ack_id])
            return True

        try:
            budget = BudgetNotification.from_pubsub_data(message.get("data", ""))
            publish_time = _parse_rfc3339(message["publishTime"], "publishTime")
        except (KeyError, ValueError) as exc:
            LOG.warning("잘못된 예산 메시지 %s를 ACK 처리합니다: %s", message_id, exc)
            self.pubsub.acknowledge([ack_id])
            return True

        policy = self.config.hard_stop
        mismatch = (
            policy.validate_message(budget, message.get("attributes"))
            if policy is not None
            else None
        )
        if mismatch:
            # The old August Budget actually produced a misleading alert during
            # migration. Only the explicitly armed Budget may now reach Discord
            # or the automatic stop path.
            LOG.warning(
                "현재 비용 가드 대상이 아닌 Budget 메시지를 ACK합니다: %s",
                mismatch,
            )
            self.state.mark_seen(message_id)
            self.pubsub.acknowledge([ack_id])
            return True

        now = self.clock().astimezone(self.config.local_timezone)
        if self.cost_guard is not None:
            # Enforce the independently authenticated actual-cost stop line
            # before a Discord delivery. An unavailable webhook must never
            # prevent a valid Billing message from stopping the VM.
            self.cost_guard.check(billing_override=budget.cost_amount)
        plan = self.state.plan(
            message_id,
            publish_time,
            budget,
            self.config.warning_thresholds,
            now,
        )
        if plan.should_send:
            guard_snapshot: CostGuardSnapshot | None = None
            if self.cost_guard is not None:
                try:
                    guard_snapshot = self.cost_guard.snapshot(
                        now, billing_override=budget.cost_amount
                    )
                except RuntimeError as exc:
                    LOG.warning("로컬 비용 추정을 생략합니다: %s", exc)
            content = render_discord_message(
                budget,
                plan,
                self.config,
                now,
                guard_snapshot=guard_snapshot,
            )
            self.discord.send(content)

        # State is durable before ACK. If ACK fails, redelivery is deduplicated.
        self.state.commit(message_id, publish_time, budget, plan)
        self.pubsub.acknowledge([ack_id])
        if plan.should_send:
            LOG.info("예산 알림을 Discord에 전송했습니다 (messageId=%s)", message_id)
        elif plan.out_of_order:
            LOG.info("역순 예산 메시지를 중복 알림 없이 ACK했습니다 (messageId=%s)", message_id)
        return True

    def process_batch(self, received_messages: list[dict[str, Any]]) -> None:
        # Newest first prevents a stale daily status when Pub/Sub returns a mixed batch.
        def published(item: dict[str, Any]) -> str:
            message = item.get("message", {})
            return str(message.get("publishTime", "")) if isinstance(message, dict) else ""

        for received in sorted(received_messages, key=published, reverse=True):
            try:
                self.process(received)
            except RemoteServiceError as exc:
                # No ACK on delivery/state failure, allowing Pub/Sub redelivery.
                LOG.warning("예산 메시지 처리를 재시도합니다: %s", exc)
            except OSError as exc:
                LOG.warning(
                    "상태 저장 실패로 예산 메시지를 재시도합니다 (%s)",
                    type(exc).__name__,
                )


def run() -> None:
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    config = Config.from_env()
    broker_path = os.environ.get("GOLE_CLOUD_BROKER_SOCKET", "").strip()
    if not broker_path:
        raise ConfigurationError("GOLE_CLOUD_BROKER_SOCKET is required")
    broker = CloudBrokerClient(broker_path, config.http_timeout_seconds)
    pubsub = BrokerPubSubClient(broker)
    state = StateStore(config.state_dir)
    discord = DiscordClient(config.webhook_url, config.http_timeout_seconds)
    cost_guard: CostGuard | None = None
    if config.hard_stop is not None and config.hard_stop.enabled:
        compute = BrokerComputeClient(broker)
        cost_guard = CostGuard(config, state, discord, compute)
    relay = Relay(
        config,
        pubsub,
        discord,
        state,
        cost_guard=cost_guard,
    )
    stopping = False
    guard_stop = threading.Event()

    def stop(_signum: int, _frame: Any) -> None:
        nonlocal stopping
        stopping = True
        guard_stop.set()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    LOG.info("GoLe GCP 예산 릴레이를 시작합니다")
    guard_thread: threading.Thread | None = None
    if cost_guard is not None:
        def guard_loop() -> None:
            next_check = time.monotonic()
            while not guard_stop.is_set():
                wait_seconds = max(0.0, next_check - time.monotonic())
                if guard_stop.wait(wait_seconds):
                    return
                try:
                    cost_guard.check()
                    Path("/tmp/gole-cost-guard-heartbeat").touch()
                except (RemoteServiceError, RuntimeError, OSError, ValueError) as exc:
                    LOG.error("실시간 비용 가드 점검 실패: %s", exc)
                next_check += cost_guard.policy.check_interval_seconds
                if next_check < time.monotonic():
                    next_check = time.monotonic()

        guard_thread = threading.Thread(
            target=guard_loop,
            name="gole-cost-guard",
            daemon=True,
        )
        guard_thread.start()

    failures = 0
    while not stopping:
        loop_delay = config.pull_interval_seconds
        try:
            messages = pubsub.pull(config.max_messages)
            failures = 0
            if messages:
                relay.process_batch(messages)
        except RemoteServiceError as exc:
            failures += 1
            loop_delay = min(60.0, 2 ** min(failures, 5)) + random.random()
            LOG.warning(
                "Pub/Sub pull 실패, %.1f초 후 재시도: %s", loop_delay, exc
            )

        if loop_delay:
            guard_stop.wait(loop_delay)
    guard_stop.set()
    if guard_thread is not None:
        guard_thread.join(timeout=10)
    LOG.info("GoLe GCP 예산 릴레이를 종료합니다")


if __name__ == "__main__":
    run()
