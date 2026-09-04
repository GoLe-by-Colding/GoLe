from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock
from datetime import datetime
from decimal import Decimal


ROOT = pathlib.Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "infra/gcp/scripts/cloud-broker.py"


def load_module():
    spec = importlib.util.spec_from_file_location("gole_cloud_broker", MODULE_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def policy_values() -> dict[str, str]:
    return {
        "PROJECT_ID": "project-72a52bf1-06aa-4519-b2c",
        "SUBSCRIPTION": "gole-billing-budget-discord",
        "VM_COST_START": "2026-09-01T19:57:05+09:00",
        "HARD_STOP_AT": "2026-10-28T01:50:00+09:00",
        "MAX_RUNTIME_HOURS": "1350",
        "FIXED_HOURLY_COST_KRW": "153.390555330",
        "HIGH_RATE_HOURLY_COST_KRW": "240.749900000",
        "RATE_TRANSITION_AT": "2026-09-06T00:00:00+09:00",
        "EXPECTED_MACHINE_TYPE": "e2-standard-2",
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
        "BUDGET_DISPLAY_NAME": "GoLe production credit guard",
        "BUDGET_AMOUNT_KRW": "370000",
        "BUDGET_PERIOD_START": "2026-09-01",
        "EXPECTED_BUDGET_ID": "b645c912-d766-43fc-8923-bff70ecfe8d8",
        "EXPECTED_BILLING_ACCOUNT_ID": "ABCDEF-123456-ABCDEF",
    }


class CloudBrokerPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.module = load_module()

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        root = pathlib.Path(self.temporary.name)
        self.module.STATE_PATH = root / "state" / "state.json"
        self.module.TX_BYTES_PATH = root / "tx_bytes"
        self.module.TX_BYTES_PATH.write_text("1000\n", encoding="ascii")
        self.policy = self.module.Policy(policy_values())

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_network_meter_is_monotonic_across_restart_increment_and_reset(self) -> None:
        cloud = self.module.Cloud(self.policy)
        baseline = 536870912 + 1000
        self.assertEqual(cloud.cumulative_tx_bytes, baseline)

        # A process restart at the same raw counter must not count bytes twice.
        cloud = self.module.Cloud(self.policy)
        self.assertEqual(cloud.cumulative_tx_bytes, baseline)

        self.module.TX_BYTES_PATH.write_text("1600\n", encoding="ascii")
        cloud._update_network_meter()
        self.assertEqual(cloud.cumulative_tx_bytes, baseline + 600)

        # A reboot/NIC reset contributes the new raw value instead of reducing
        # the period total.
        self.module.TX_BYTES_PATH.write_text("25\n", encoding="ascii")
        cloud._update_network_meter()
        self.assertEqual(cloud.cumulative_tx_bytes, baseline + 625)
        persisted = json.loads(self.module.STATE_PATH.read_text(encoding="utf-8"))
        self.assertEqual(persisted["cumulative_tx_bytes"], baseline + 625)
        self.assertEqual(persisted["deadline_alerts"], [])

    def test_legacy_schema_migration_writes_state_once_when_meter_changes(self) -> None:
        self.module.STATE_PATH.parent.mkdir(mode=0o700)
        self.module.STATE_PATH.write_text(
            json.dumps(
                {
                    "latest_billing": "0",
                    "last_tx_raw": 900,
                    "cumulative_tx_bytes": 536871812,
                }
            )
            + "\n",
            encoding="utf-8",
        )
        with mock.patch.object(
            self.module.Cloud, "_write_state", autospec=True
        ) as write_state:
            self.module.Cloud(self.policy)
        write_state.assert_called_once()

    def test_corrupt_state_and_missing_meter_fail_closed(self) -> None:
        self.module.STATE_PATH.parent.mkdir(mode=0o700)
        self.module.STATE_PATH.write_text("not-json\n", encoding="utf-8")
        with self.assertRaises(self.module.BrokerError):
            self.module.Cloud(self.policy)
        self.module.STATE_PATH.unlink()
        self.module.TX_BYTES_PATH.unlink()
        with self.assertRaises(self.module.BrokerError):
            self.module.Cloud(self.policy)

    def test_budget_identity_mismatch_never_advances_root_state(self) -> None:
        cloud = self.module.Cloud(self.policy)
        cloud._write_state = lambda: None
        payload = {
            "costAmount": "1000",
            "costIntervalStart": "2026-09-01T00:00:00Z",
            "budgetDisplayName": "GoLe production credit guard",
            "currencyCode": "KRW",
            "budgetAmount": "370000",
        }
        import base64

        encoded = base64.b64encode(json.dumps(payload).encode()).decode()
        cloud._observe_budget(
            [
                {
                    "message": {
                        "attributes": {
                            "schemaVersion": "1.0",
                            "budgetId": "00000000-0000-0000-0000-000000000000",
                            "billingAccountId": "ABCDEF-123456-ABCDEF",
                        },
                        "data": encoded,
                    }
                }
            ]
        )
        self.assertEqual(cloud.latest_billing, Decimal("0"))

    def test_absolute_cutoff_and_all_in_policy_use_cumulative_network(self) -> None:
        at_cutoff = datetime.fromisoformat("2026-10-28T01:50:00+09:00")
        self.assertEqual(
            self.policy.stop_reason(Decimal("0"), at_cutoff, 536870912),
            "absolute-cutoff",
        )
        before = datetime.fromisoformat("2026-10-20T00:00:00+09:00")
        self.assertEqual(
            self.policy.stop_reason(Decimal("0"), before, 31 * 1024**3),
            "network-limit",
        )

    def test_piecewise_cost_and_retained_tail_stay_below_hard_all_in_limit(self) -> None:
        cutoff = datetime.fromisoformat("2026-10-28T01:50:00+09:00")
        current, tail, all_in, runtime, tx_gib = self.policy.projected_all_in(
            Decimal("0"), cutoff, 30 * 1024**3
        )
        self.assertEqual(
            runtime.quantize(Decimal("0.000001")), Decimal("1349.881944")
        )
        self.assertEqual(tx_gib, Decimal("30"))
        self.assertEqual(
            all_in.quantize(Decimal("0.000001")), Decimal("327556.956498")
        )
        self.assertGreater(current, Decimal("295000"))
        self.assertGreater(tail, Decimal("2100"))
        self.assertLess(all_in, Decimal("350000"))

    def test_deadline_reminders_are_durable_and_never_auto_delete_resources(self) -> None:
        cloud = self.module.Cloud(self.policy)
        sent: list[str] = []
        cloud._send_deadline_alert = lambda key, _now: sent.append(key) is None
        at_seven_days = datetime.fromisoformat("2026-10-21T23:59:59+09:00")
        cloud._notify_deadline_reminders(at_seven_days)
        self.assertEqual(sent, ["d-14", "d-7"])
        cloud._notify_deadline_reminders(at_seven_days)
        self.assertEqual(sent, ["d-14", "d-7"])
        persisted = json.loads(self.module.STATE_PATH.read_text(encoding="utf-8"))
        self.assertEqual(persisted["deadline_alerts"], ["d-14", "d-7"])

        # A broker restart retains exactly-once successful reminders.
        cloud = self.module.Cloud(self.policy)
        self.assertEqual(cloud.deadline_alerts, {"d-14", "d-7"})
        source = MODULE_PATH.read_text(encoding="utf-8")
        self.assertNotIn("compute snapshots delete", source)
        self.assertNotIn("compute disks delete", source)

    def test_policy_rejects_relaxed_machine_snapshot_and_baseline_values(self) -> None:
        for key, value in (
            ("EXPECTED_MACHINE_TYPE", "e2-standard-4"),
            ("SNAPSHOT_RETENTION_HOURS", "168"),
            ("NETWORK_PERIOD_BASELINE_BYTES", "0"),
            ("VAT_RATE", "0"),
        ):
            with self.subTest(key=key):
                values = policy_values()
                values[key] = value
                with self.assertRaises(self.module.BrokerError):
                    self.module.Policy(values)


if __name__ == "__main__":
    unittest.main()
