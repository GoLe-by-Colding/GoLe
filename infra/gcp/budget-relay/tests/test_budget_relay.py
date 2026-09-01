import base64
import json
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from budget_relay import (  # noqa: E402
    BudgetNotification,
    Config,
    ConfigurationError,
    CostGuard,
    DiscordClient,
    Relay,
    RemoteServiceError,
    StateStore,
    calculate_cost_guard_snapshot,
    render_discord_message,
)


NOW = datetime(2026, 9, 1, 12, 0, tzinfo=timezone.utc)


def encoded_budget(
    cost="300000",
    alert="0.75",
    forecast=None,
    *,
    period_start="2026-09-01T00:00:00Z",
    display_name="GoLe production budget",
    budget_amount="395600.60",
):
    payload = {
        "budgetDisplayName": display_name,
        "costAmount": float(cost),
        "costIntervalStart": period_start,
        "budgetAmount": float(budget_amount),
        "budgetAmountType": "SPECIFIED_AMOUNT",
        "currencyCode": "KRW",
    }
    if alert is not None:
        payload["alertThresholdExceeded"] = float(alert)
    if forecast is not None:
        payload["forecastThresholdExceeded"] = float(forecast)
    return base64.b64encode(json.dumps(payload).encode()).decode()


def envelope(
    message_id="m-1",
    cost="300000",
    publish="2026-09-01T11:59:00Z",
    *,
    forecast=None,
    attributes=None,
    period_start="2026-09-01T00:00:00Z",
    display_name="GoLe production budget",
    budget_amount="395600.60",
    alert="0.75",
):
    return {
        "ackId": f"ack-{message_id}",
        "message": {
            "messageId": message_id,
            "publishTime": publish,
            "data": encoded_budget(
                cost,
                alert=alert,
                forecast=forecast,
                period_start=period_start,
                display_name=display_name,
                budget_amount=budget_amount,
            ),
            **({"attributes": attributes} if attributes is not None else {}),
        },
    }


def config(state_dir):
    return Config.from_env(
        {
            "GCP_BUDGET_PUBSUB_SUBSCRIPTION": "subscription",
            "DISCORD_OPERATIONS_WEBHOOK_URL": "https://discord.com/api/webhooks/id/token",
            "GCP_CREDIT_AMOUNT_KRW": "395600.60",
            "GCP_CREDIT_DEADLINE": "2026-10-28",
            "GCP_FIXED_HOURLY_COST_KRW": "262.46",
            "BUDGET_STATE_DIR": str(state_dir),
        }
    )


def guard_attributes():
    return {
        "budgetId": "b645c912-d766-43fc-8923-bff70ecfe8d8",
        "billingAccountId": "01B490-1BC53A-33E611",
        "schemaVersion": "1.0",
    }


def guard_config(state_dir, tx_path, boot_path, **overrides):
    env = {
        "GCP_BUDGET_PUBSUB_SUBSCRIPTION": "subscription",
        "DISCORD_OPERATIONS_WEBHOOK_URL": "https://discord.com/api/webhooks/id/token",
        "GCP_PROJECT_ID": "project-72a52bf1-06aa-4519-b2c",
        "GCP_CREDIT_AMOUNT_KRW": "395600.60",
        "GCP_CREDIT_DEADLINE": "2026-10-28T23:59:59+09:00",
        "GCP_FIXED_HOURLY_COST_KRW": "231.249894200",
        "BUDGET_STATE_DIR": str(state_dir),
        "GCP_HARD_STOP_ENABLED": "true",
        "GCP_HARD_STOP_DRY_RUN": "false",
        "GCP_HARD_STOP_BILLING_COST_KRW": "320000",
        "GCP_HARD_STOP_MIN_RESERVE_KRW": "75000",
        "GCP_HARD_STOP_ALL_IN_COST_KRW": "350000",
        "GCP_HARD_STOP_EXPECTED_BUDGET_KRW": "370000",
        "GCP_HARD_STOP_BUDGET_ID": "b645c912-d766-43fc-8923-bff70ecfe8d8",
        "GCP_HARD_STOP_BILLING_ACCOUNT_ID": "01B490-1BC53A-33E611",
        "GCP_HARD_STOP_BUDGET_DISPLAY_NAME": "GoLe production credit guard",
        "GCP_HARD_STOP_PERIOD_START": "2026-09-01",
        "GCP_VM_COST_START": "2026-09-01T19:57:05+09:00",
        "GCP_HARD_STOP_AT": "2026-10-26T19:50:00+09:00",
        "GCP_HARD_STOP_ARM_ID": "2026-09-credit-v1",
        "GCP_INSTANCE_ZONE": "asia-northeast3-a",
        "GCP_INSTANCE_NAME": "gole-production",
        "GCP_NETWORK_TX_BYTES_PATH": str(tx_path),
        "GCP_HOST_BOOT_ID_PATH": str(boot_path),
    }
    env.update(overrides)
    return Config.from_env(env)


class FakePubSub:
    def __init__(self):
        self.acked = []

    def acknowledge(self, ack_ids):
        self.acked.extend(ack_ids)


class FakeDiscord:
    def __init__(self, failure=None):
        self.messages = []
        self.failure = failure

    def send(self, content):
        if self.failure:
            raise self.failure
        self.messages.append(content)


class FakeCompute:
    def __init__(self, failure=None):
        self.request_ids = []
        self.failure = failure

    def stop(self, request_id):
        self.request_ids.append(request_id)
        if self.failure:
            raise self.failure


class BudgetRelayTests(unittest.TestCase):
    def test_discord_client_uses_explicit_user_agent(self):
        class Response:
            status = 204

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

        def open_request(request, timeout):
            self.assertEqual(timeout, 5)
            self.assertEqual(
                request.get_header("User-agent"), "GoLe-Budget-Relay/1.0"
            )
            return Response()

        with patch("budget_relay.urllib.request.urlopen", side_effect=open_request):
            DiscordClient(
                "https://discord.com/api/webhooks/id/token", timeout=5
            ).send("test")

    def test_decodes_official_budget_payload(self):
        budget = BudgetNotification.from_pubsub_data(encoded_budget())
        self.assertEqual(budget.display_name, "GoLe production budget")
        self.assertEqual(budget.cost_amount, Decimal("300000.0"))
        self.assertEqual(budget.currency_code, "KRW")
        self.assertEqual(budget.alert_threshold, Decimal("0.75"))

    def test_success_is_persisted_before_ack_and_duplicate_is_suppressed(self):
        with tempfile.TemporaryDirectory() as directory:
            cfg = config(directory)
            state = StateStore(Path(directory))
            pubsub = FakePubSub()
            discord = FakeDiscord()
            relay = Relay(cfg, pubsub, discord, state, clock=lambda: NOW)

            self.assertTrue(relay.process(envelope()))
            self.assertEqual(len(discord.messages), 1)
            self.assertEqual(pubsub.acked, ["ack-m-1"])
            self.assertTrue((Path(directory) / "budget-relay-state.json").exists())

            reloaded = StateStore(Path(directory))
            relay = Relay(cfg, pubsub, discord, reloaded, clock=lambda: NOW)
            self.assertTrue(relay.process(envelope()))
            self.assertEqual(len(discord.messages), 1)
            self.assertEqual(pubsub.acked, ["ack-m-1", "ack-m-1"])

    def test_discord_failure_is_not_acked_or_persisted(self):
        with tempfile.TemporaryDirectory() as directory:
            cfg = config(directory)
            state = StateStore(Path(directory))
            pubsub = FakePubSub()
            discord = FakeDiscord(RemoteServiceError("Discord delivery failed"))
            relay = Relay(cfg, pubsub, discord, state, clock=lambda: NOW)

            with self.assertRaises(RemoteServiceError):
                relay.process(envelope())
            self.assertEqual(pubsub.acked, [])
            self.assertFalse(state.is_seen("m-1"))

    def test_out_of_order_message_does_not_send_or_rewind_latest(self):
        with tempfile.TemporaryDirectory() as directory:
            cfg = config(directory)
            state = StateStore(Path(directory))
            pubsub = FakePubSub()
            discord = FakeDiscord()
            relay = Relay(cfg, pubsub, discord, state, clock=lambda: NOW)

            relay.process(envelope("new", "350000", "2026-09-01T11:59:00Z"))
            relay.process(envelope("old", "200000", "2026-09-01T10:00:00Z"))

            self.assertEqual(len(discord.messages), 1)
            period = next(iter(state.data["periods"].values()))
            self.assertEqual(period["latest_cost_amount"], "350000.0")
            self.assertEqual(pubsub.acked, ["ack-new", "ack-old"])

    def test_new_threshold_sends_even_after_daily_status(self):
        with tempfile.TemporaryDirectory() as directory:
            cfg = config(directory)
            state = StateStore(Path(directory))
            pubsub = FakePubSub()
            discord = FakeDiscord()
            relay = Relay(cfg, pubsub, discord, state, clock=lambda: NOW)

            relay.process(envelope("low", "200000", "2026-09-01T10:00:00Z"))
            relay.process(envelope("high", "370000", "2026-09-01T12:00:00Z"))
            relay.process(envelope("repeat", "375000", "2026-09-01T12:05:00Z"))

            self.assertEqual(len(discord.messages), 2)
            self.assertIn("90.0% 임계치", discord.messages[1])
            self.assertEqual(len(pubsub.acked), 3)

    def test_projection_warns_if_credit_will_be_exceeded(self):
        with tempfile.TemporaryDirectory() as directory:
            cfg = config(directory)
            budget = BudgetNotification.from_pubsub_data(encoded_budget("300000"))
            plan = StateStore(Path(directory)).plan(
                "m-1", NOW, budget, cfg.warning_thresholds, NOW
            )
            message = render_discord_message(budget, plan, cfg, NOW)
            self.assertIn("초과할 것으로 예상", message)
            self.assertIn("예상 크레딧 잔액", message)
            self.assertNotIn(cfg.webhook_url, message)

    def test_hard_stop_accepts_only_exact_budget_identity_and_period(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cfg = guard_config(root, root / "tx_bytes", root / "boot_id")
            state = StateStore(root)
            pubsub = FakePubSub()
            discord = FakeDiscord()
            relay = Relay(cfg, pubsub, discord, state, clock=lambda: NOW)

            valid_attributes = guard_attributes()
            mismatches = [
                ("missing-attributes", None),
                (
                    "wrong-budget-id",
                    {**valid_attributes, "budgetId": "old-budget"},
                ),
                (
                    "wrong-billing-account",
                    {**valid_attributes, "billingAccountId": "other-account"},
                ),
                (
                    "wrong-schema",
                    {**valid_attributes, "schemaVersion": "0.1"},
                ),
            ]
            for message_id, attributes in mismatches:
                with self.subTest(message_id=message_id):
                    self.assertTrue(
                        relay.process(
                            envelope(
                                message_id,
                                "319999",
                                attributes=attributes,
                                period_start="2026-09-01T07:00:00Z",
                                display_name="GoLe production credit guard",
                                budget_amount="370000",
                            )
                        )
                    )

            payload_mismatches = [
                {
                    "message_id": "wrong-display-name",
                    "display_name": "GoLe old production budget",
                },
                {
                    "message_id": "wrong-budget-amount",
                    "budget_amount": "369999",
                },
                {
                    "message_id": "wrong-period",
                    "period_start": "2026-08-05T07:00:00Z",
                },
            ]
            for case in payload_mismatches:
                message_id = case.pop("message_id")
                with self.subTest(message_id=message_id):
                    self.assertTrue(
                        relay.process(
                            envelope(
                                message_id,
                                "319999",
                                attributes=valid_attributes,
                                period_start=case.get(
                                    "period_start", "2026-09-01T07:00:00Z"
                                ),
                                display_name=case.get(
                                    "display_name",
                                    "GoLe production credit guard",
                                ),
                                budget_amount=case.get(
                                    "budget_amount", "370000"
                                ),
                            )
                        )
                    )

            self.assertEqual(discord.messages, [])
            self.assertEqual(len(pubsub.acked), 7)
            self.assertTrue(
                relay.process(
                    envelope(
                        "armed-budget",
                        "319999",
                        attributes=valid_attributes,
                        period_start="2026-09-01T07:00:00Z",
                        display_name="GoLe production credit guard",
                        budget_amount="370000",
                    )
                )
            )
            self.assertEqual(len(discord.messages), 1)
            self.assertEqual(pubsub.acked[-1], "ack-armed-budget")

    def test_forecast_only_notification_never_stops_vm(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            pubsub = FakePubSub()
            discord = FakeDiscord()
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, discord, compute, clock=lambda: NOW
            )
            relay = Relay(
                cfg,
                pubsub,
                discord,
                state,
                clock=lambda: NOW,
                cost_guard=guard,
            )

            self.assertTrue(
                relay.process(
                    envelope(
                        "forecast-only",
                        "1000",
                        forecast="1.0",
                        alert=None,
                        attributes=guard_attributes(),
                        period_start="2026-09-01T07:00:00Z",
                        display_name="GoLe production credit guard",
                        budget_amount="370000",
                    )
                )
            )
            snapshot = guard.check()

            self.assertIsNone(snapshot.stop_reason)
            self.assertEqual(compute.request_ids, [])
            self.assertIsNone(state.guard_trip_reason(cfg.hard_stop.arm_id))
            self.assertTrue(
                any("예측비용 100.0% 임계치" in item for item in discord.messages)
            )

    def test_billing_320000_stops_vm(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            pubsub = FakePubSub()
            discord = FakeDiscord()
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, discord, compute, clock=lambda: NOW
            )
            relay = Relay(
                cfg,
                pubsub,
                discord,
                state,
                clock=lambda: NOW,
                cost_guard=guard,
            )

            relay.process(
                envelope(
                    "billing-stop",
                    "320000",
                    attributes=guard_attributes(),
                    period_start="2026-09-01T07:00:00Z",
                    display_name="GoLe production credit guard",
                    budget_amount="370000",
                )
            )
            snapshot = guard.check()

            self.assertEqual(snapshot.stop_reason, "billing-limit")
            self.assertEqual(len(compute.request_ids), 1)
            self.assertEqual(
                state.guard_trip_reason(cfg.hard_stop.arm_id),
                "billing-limit",
            )

    def test_missing_webhook_still_allows_config_and_billing_stop(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cfg = guard_config(
                root,
                tx_path,
                boot_path,
                DISCORD_OPERATIONS_WEBHOOK_URL="",
            )
            state = StateStore(root)
            compute = FakeCompute()
            guard = CostGuard(
                cfg,
                state,
                DiscordClient(cfg.webhook_url, timeout=5),
                compute,
                clock=lambda: NOW,
            )

            snapshot = guard.check(billing_override=Decimal("320000"))

            self.assertEqual(cfg.webhook_url, "")
            self.assertEqual(snapshot.stop_reason, "billing-limit")
            self.assertEqual(len(compute.request_ids), 1)

    def test_billing_gross_cost_is_not_charged_vat_twice(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cfg = guard_config(root, root / "tx_bytes", root / "boot_id")

            snapshot = calculate_cost_guard_snapshot(
                cfg,
                NOW,
                observed_billing_gross=Decimal("300000"),
                network_bytes=0,
            )

            self.assertEqual(snapshot.estimated_current_gross, Decimal("300000"))
            self.assertEqual(
                snapshot.all_in_if_stopped_gross,
                Decimal("300000")
                + snapshot.stopped_resources_remaining_pre_tax
                * Decimal("1.10"),
            )

    def test_state_save_error_does_not_prevent_compute_stop(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, FakeDiscord(), compute, clock=lambda: NOW
            )

            with patch.object(state, "_save", side_effect=OSError("read-only")):
                snapshot = guard.check()

            self.assertIsNone(snapshot.stop_reason)
            self.assertEqual(len(compute.request_ids), 1)

    def test_corrupt_state_load_immediately_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            (root / "budget-relay-state.json").write_text(
                "{not-json", encoding="utf-8"
            )
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, FakeDiscord(), compute, clock=lambda: NOW
            )

            snapshot = guard.check()

            self.assertIsNone(snapshot.stop_reason)
            self.assertIsNotNone(state.load_error)
            self.assertEqual(len(compute.request_ids), 1)
            self.assertEqual(
                state.guard_trip_reason(cfg.hard_stop.arm_id),
                "state-unavailable",
            )

    def test_failed_stop_retries_use_unique_request_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cutoff = datetime.fromisoformat("2026-10-26T19:50:00+09:00")
            now = [cutoff]
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            compute = FakeCompute(RemoteServiceError("Compute unavailable"))
            guard = CostGuard(
                cfg, state, FakeDiscord(), compute, clock=lambda: now[0]
            )

            with self.assertRaises(RemoteServiceError):
                guard.check()
            now[0] += timedelta(seconds=301)
            with self.assertRaises(RemoteServiceError):
                guard.check()

            self.assertEqual(len(compute.request_ids), 2)
            self.assertEqual(len(set(compute.request_ids)), 2)

    def test_discord_failure_does_not_prevent_stop(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            pubsub = FakePubSub()
            discord = FakeDiscord(RemoteServiceError("Discord unavailable"))
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, discord, compute, clock=lambda: NOW
            )
            relay = Relay(
                cfg,
                pubsub,
                discord,
                state,
                clock=lambda: NOW,
                cost_guard=guard,
            )

            with self.assertRaises(RemoteServiceError):
                relay.process(
                    envelope(
                        "billing-stop-discord-down",
                        "320000",
                        attributes=guard_attributes(),
                        period_start="2026-09-01T07:00:00Z",
                        display_name="GoLe production credit guard",
                        budget_amount="370000",
                    )
                )

            self.assertEqual(len(compute.request_ids), 1)
            self.assertEqual(
                state.guard_trip_reason(cfg.hard_stop.arm_id),
                "billing-limit",
            )

    def test_persisted_trip_blocks_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            cfg = guard_config(root, tx_path, boot_path)
            initial = StateStore(root)
            initial.trip_guard(cfg.hard_stop.arm_id, "billing-limit", NOW)

            reloaded = StateStore(root)
            compute = FakeCompute()
            guard = CostGuard(
                cfg,
                reloaded,
                FakeDiscord(),
                compute,
                clock=lambda: NOW + timedelta(seconds=301),
            )
            snapshot = guard.check()

            self.assertIsNone(snapshot.stop_reason)
            self.assertEqual(len(compute.request_ids), 1)
            self.assertEqual(
                reloaded.guard_trip_reason(cfg.hard_stop.arm_id),
                "billing-limit",
            )

    def test_network_30_gib_stops_vm(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text(str(30 * 1024**3), encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, FakeDiscord(), compute, clock=lambda: NOW
            )

            snapshot = guard.check()

            self.assertEqual(snapshot.network_gib, Decimal("30"))
            self.assertEqual(snapshot.stop_reason, "network-limit")
            self.assertEqual(len(compute.request_ids), 1)
            self.assertEqual(
                state.guard_trip_reason(cfg.hard_stop.arm_id),
                "network-limit",
            )

    def test_absolute_cutoff_stops_vm(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tx_path = root / "tx_bytes"
            boot_path = root / "boot_id"
            tx_path.write_text("0", encoding="utf-8")
            boot_path.write_text("boot-a", encoding="utf-8")
            cutoff = datetime.fromisoformat("2026-10-26T19:50:00+09:00")
            cfg = guard_config(root, tx_path, boot_path)
            state = StateStore(root)
            compute = FakeCompute()
            guard = CostGuard(
                cfg, state, FakeDiscord(), compute, clock=lambda: cutoff
            )

            snapshot = guard.check()

            self.assertEqual(snapshot.stop_reason, "absolute-cutoff")
            self.assertEqual(len(compute.request_ids), 1)
            self.assertEqual(
                state.guard_trip_reason(cfg.hard_stop.arm_id),
                "absolute-cutoff",
            )

    def test_minimum_reserve_rejects_unsafe_billing_limit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(
                ConfigurationError, "does not leave the minimum reserve"
            ):
                guard_config(
                    root,
                    root / "tx_bytes",
                    root / "boot_id",
                    GCP_HARD_STOP_BILLING_COST_KRW="320601",
                )


if __name__ == "__main__":
    unittest.main()
