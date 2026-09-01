#!/usr/bin/env python3
"""Google Cloud Billing Budget Pub/Sub to Discord relay.

The service deliberately uses only Python's standard library. It obtains an
OAuth token from the Compute Engine metadata server, pulls budget messages,
and acknowledges a message only after any required Discord notification has
been delivered and durable deduplication state has been written.
"""

from __future__ import annotations

import base64
import hashlib
import json
import logging
import os
import random
import signal
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import date, datetime, time as datetime_time, timedelta, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Callable, Iterable


LOG = logging.getLogger("gole-budget-relay")
METADATA_ROOT = "http://metadata.google.internal/computeMetadata/v1"
PUBSUB_ROOT = "https://pubsub.googleapis.com/v1"
STATE_VERSION = 1


class ConfigurationError(ValueError):
    """Raised when required configuration is missing or invalid."""


class RemoteServiceError(RuntimeError):
    """A safe-to-log remote error that never contains a credential URL."""


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
class Config:
    subscription: str
    webhook_url: str
    credit_amount_krw: Decimal
    credit_deadline: datetime
    fixed_hourly_cost_krw: Decimal
    warning_thresholds: tuple[Decimal, ...]
    state_dir: Path
    timezone_offset_hours: int
    max_messages: int
    pull_interval_seconds: float
    http_timeout_seconds: float
    project_id: str | None = None

    @classmethod
    def from_env(cls, env: dict[str, str] | None = None) -> "Config":
        values = os.environ if env is None else env

        def required(name: str) -> str:
            value = values.get(name, "").strip()
            if not value:
                raise ConfigurationError(f"{name} is required")
            return value

        subscription = required("GCP_BUDGET_PUBSUB_SUBSCRIPTION")
        webhook_url = required("DISCORD_OPERATIONS_WEBHOOK_URL")
        if not webhook_url.startswith("https://discord.com/api/webhooks/") and not webhook_url.startswith(
            "https://discordapp.com/api/webhooks/"
        ):
            raise ConfigurationError("DISCORD_OPERATIONS_WEBHOOK_URL is not a Discord webhook")

        credit = _decimal(required("GCP_CREDIT_AMOUNT_KRW"), "GCP_CREDIT_AMOUNT_KRW")
        hourly = _decimal(
            required("GCP_FIXED_HOURLY_COST_KRW"), "GCP_FIXED_HOURLY_COST_KRW"
        )
        if credit <= 0 or hourly < 0:
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
        timeout = float(values.get("BUDGET_HTTP_TIMEOUT_SECONDS", "30"))
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
            warning_thresholds=thresholds,
            state_dir=Path(values.get("BUDGET_STATE_DIR", "/state")),
            timezone_offset_hours=offset,
            max_messages=max_messages,
            pull_interval_seconds=pull_interval,
            http_timeout_seconds=timeout,
            project_id=values.get("GCP_PROJECT_ID", "").strip() or None,
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
        self.data: dict[str, Any] = {
            "version": STATE_VERSION,
            "seen_message_ids": [],
            "periods": {},
        }
        self._load()

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
            self.data = loaded
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            raise RuntimeError(f"cannot load budget relay state: {type(exc).__name__}") from exc

    def is_seen(self, message_id: str) -> bool:
        return message_id in self.data["seen_message_ids"]

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

    def commit(
        self,
        message_id: str,
        publish_time: datetime,
        budget: BudgetNotification,
        plan: NotificationPlan,
    ) -> None:
        seen: list[str] = self.data["seen_message_ids"]
        if message_id not in seen:
            seen.append(message_id)
            del seen[:-self.max_seen]

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

    def _save(self) -> None:
        self.state_dir.mkdir(parents=True, exist_ok=True)
        fd, temporary = tempfile.mkstemp(
            prefix=".budget-relay-", suffix=".json", dir=self.state_dir
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(self.data, handle, ensure_ascii=False, sort_keys=True)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, self.path)
        finally:
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


def render_discord_message(
    budget: BudgetNotification,
    plan: NotificationPlan,
    config: Config,
    now: datetime,
) -> str:
    now_local = now.astimezone(config.local_timezone)
    deadline = config.credit_deadline.astimezone(config.local_timezone)
    remaining_hours = Decimal(
        str(max(0.0, (deadline - now_local).total_seconds()) / 3600)
    )
    future_fixed = config.fixed_hourly_cost_krw * remaining_hours

    if budget.currency_code == "KRW":
        current_credit_remaining = config.credit_amount_krw - budget.cost_amount
        projected_total = budget.cost_amount + future_fixed
        projected_credit_remaining = config.credit_amount_krw - projected_total
        projection_lines = [
            f"- 설정 크레딧: {_money(config.credit_amount_krw, 'KRW')}",
            f"- 현재 비용 차감 잔액: {_money(current_credit_remaining, 'KRW')}",
            f"- 만료일까지 예상 추가 고정비: {_money(future_fixed, 'KRW')}",
            f"- 만료 시점 예상 총비용: {_money(projected_total, 'KRW')}",
            f"- 예상 크레딧 잔액: {_money(projected_credit_remaining, 'KRW')}",
        ]
        overrun = projected_credit_remaining < 0
    else:
        projection_lines = [
            f"- 설정 크레딧: {_money(config.credit_amount_krw, 'KRW')}",
            f"- 만료일까지 예상 추가 고정비: {_money(future_fixed, 'KRW')}",
            "- 크레딧 잔액 추정: 비용 통화가 KRW가 아니어서 생략",
        ]
        overrun = False

    level = plan.observed_level
    if overrun or level >= Decimal("1"):
        icon = "🚨"
    elif level >= Decimal("0.9"):
        icon = "🔴"
    elif level >= Decimal("0.75"):
        icon = "🟠"
    elif level >= Decimal("0.5"):
        icon = "🟡"
    else:
        icon = "🟢"

    reasons: list[str] = []
    if any(item.startswith("actual:") for item in plan.threshold_events):
        actual_levels = [Decimal(item.split(":", 1)[1]) for item in plan.threshold_events if item.startswith("actual:")]
        reasons.append(f"실제비용 {_percentage(max(actual_levels))} 임계치")
    if any(item.startswith("forecast:") for item in plan.threshold_events):
        forecast_levels = [Decimal(item.split(":", 1)[1]) for item in plan.threshold_events if item.startswith("forecast:")]
        reasons.append(f"예측비용 {_percentage(max(forecast_levels))} 임계치")
    if not reasons:
        reasons.append("일일 현황")

    d_day = (deadline.date() - now_local.date()).days
    budget_remaining = budget.budget_amount - budget.cost_amount
    lines = [
        f"{icon} **GoLe GCP 예산 알림 · {' / '.join(reasons)}**",
        f"- 예산: **{_money(budget.cost_amount, budget.currency_code)} / {_money(budget.budget_amount, budget.currency_code)}** ({_percentage(budget.actual_ratio)})",
        f"- 예산 잔액: **{_money(budget_remaining, budget.currency_code)}**",
        *projection_lines,
        f"- 고정비 가정: {_money(config.fixed_hourly_cost_krw, 'KRW')}/시간",
        f"- 크레딧 만료: {deadline.date().isoformat()} (D-{max(d_day, 0)})",
        f"- 비용 구간 시작: {budget.interval_start.date().isoformat()}",
        "_공식 예산의 실제비용과 설정한 시간당 고정비가 유지된다는 단순 추정입니다._",
    ]
    if overrun:
        lines.insert(1, "**현재 사양을 유지하면 설정 크레딧을 초과할 것으로 예상됩니다.**")
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
                raise RemoteServiceError(
                    f"Pub/Sub {action} failed ({type(exc).__name__})"
                ) from exc
            except json.JSONDecodeError as exc:
                raise RemoteServiceError(f"Pub/Sub {action} returned invalid JSON") from exc
        raise RemoteServiceError(f"Pub/Sub {action} authorization failed")

    def pull(self, max_messages: int) -> list[dict[str, Any]]:
        response = self._post("pull", {"maxMessages": max_messages})
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


class Relay:
    def __init__(
        self,
        config: Config,
        pubsub: PubSubClient,
        discord: DiscordClient,
        state: StateStore,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self.config = config
        self.pubsub = pubsub
        self.discord = discord
        self.state = state
        self.clock = clock or (lambda: datetime.now(timezone.utc))

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

        now = self.clock().astimezone(self.config.local_timezone)
        plan = self.state.plan(
            message_id,
            publish_time,
            budget,
            self.config.warning_thresholds,
            now,
        )
        if plan.should_send:
            content = render_discord_message(budget, plan, self.config, now)
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
    credentials = MetadataCredentials(config.http_timeout_seconds)
    pubsub = PubSubClient(
        config.subscription,
        config.project_id,
        credentials,
        config.http_timeout_seconds,
    )
    relay = Relay(
        config,
        pubsub,
        DiscordClient(config.webhook_url, config.http_timeout_seconds),
        StateStore(config.state_dir),
    )
    stopping = False

    def stop(_signum: int, _frame: Any) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    LOG.info("GoLe GCP 예산 릴레이를 시작합니다")
    failures = 0
    while not stopping:
        try:
            messages = pubsub.pull(config.max_messages)
            failures = 0
            if messages:
                relay.process_batch(messages)
            if config.pull_interval_seconds:
                time.sleep(config.pull_interval_seconds)
        except RemoteServiceError as exc:
            failures += 1
            delay = min(60.0, 2 ** min(failures, 5)) + random.random()
            LOG.warning("Pub/Sub pull 실패, %.1f초 후 재시도: %s", delay, exc)
            time.sleep(delay)
    LOG.info("GoLe GCP 예산 릴레이를 종료합니다")


if __name__ == "__main__":
    run()
