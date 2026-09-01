import base64
import json
import tempfile
import unittest
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from budget_relay import (  # noqa: E402
    BudgetNotification,
    Config,
    Relay,
    RemoteServiceError,
    StateStore,
    render_discord_message,
)


NOW = datetime(2026, 9, 1, 12, 0, tzinfo=timezone.utc)


def encoded_budget(cost="300000", alert="0.75", forecast=None):
    payload = {
        "budgetDisplayName": "GoLe production budget",
        "costAmount": float(cost),
        "costIntervalStart": "2026-09-01T00:00:00Z",
        "budgetAmount": 395600.60,
        "budgetAmountType": "SPECIFIED_AMOUNT",
        "currencyCode": "KRW",
        "alertThresholdExceeded": float(alert),
    }
    if forecast is not None:
        payload["forecastThresholdExceeded"] = float(forecast)
    return base64.b64encode(json.dumps(payload).encode()).decode()


def envelope(message_id="m-1", cost="300000", publish="2026-09-01T11:59:00Z"):
    return {
        "ackId": f"ack-{message_id}",
        "message": {
            "messageId": message_id,
            "publishTime": publish,
            "data": encoded_budget(cost),
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


class BudgetRelayTests(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
