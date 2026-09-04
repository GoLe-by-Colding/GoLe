from __future__ import annotations

import copy
import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).parents[1] / "scripts" / "verify-operator-iam-plan.py"
SPEC = importlib.util.spec_from_file_location("verify_operator_iam_plan", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

PROJECT = "project-72a52bf1-06aa-4519-b2c"
RUNTIME = f"gole-production-runtime@{PROJECT}.iam.gserviceaccount.com"


def change(address: str, after: dict, actions: list[str]) -> dict:
    return {
        "address": address,
        "change": {"actions": actions, "before": None, "after": after},
    }


def safe_plan() -> dict:
    return {
        "resource_changes": [
            change(
                "google_project_iam_member.operator_os_admin",
                {
                    "project": PROJECT,
                    "role": "roles/compute.osAdminLogin",
                    "member": "user:coldingcontact@gmail.com",
                },
                ["create"],
            ),
            change(
                "google_project_iam_member.operator_iap_tunnel",
                {
                    "project": PROJECT,
                    "role": "roles/iap.tunnelResourceAccessor",
                    "member": "user:coldingcontact@gmail.com",
                },
                ["create"],
            ),
            change(
                "google_service_account_iam_member.operator_service_account_user",
                {
                    "service_account_id": f"projects/{PROJECT}/serviceAccounts/{RUNTIME}",
                    "role": "roles/iam.serviceAccountUser",
                    "member": "user:coldingcontact@gmail.com",
                },
                ["create"],
            ),
            change(
                "google_project_service.iam",
                {"project": PROJECT, "service": "iam.googleapis.com"},
                ["no-op"],
            ),
            change(
                "google_service_account.production_runtime",
                {
                    "account_id": "gole-production-runtime",
                    "email": RUNTIME,
                },
                ["no-op"],
            ),
        ]
    }


class OperatorIamPlanPolicyTest(unittest.TestCase):
    def test_allows_only_exact_operator_bindings_and_dependencies(self) -> None:
        MODULE.validate(safe_plan(), PROJECT)
        plan = safe_plan()
        for item in plan["resource_changes"][:3]:
            item["change"]["before"] = copy.deepcopy(item["change"]["after"])
            item["change"]["actions"] = ["no-op"]
        MODULE.validate(plan, PROJECT)

    def test_rejects_unrelated_resource_privilege_and_target_changes(self) -> None:
        plans = []
        unrelated = safe_plan()
        unrelated["resource_changes"].append(
            change("google_compute_instance.extra", {"name": "extra"}, ["create"])
        )
        plans.append(unrelated)
        privilege = safe_plan()
        privilege["resource_changes"][0]["change"]["after"]["role"] = "roles/owner"
        plans.append(privilege)
        target = safe_plan()
        target["resource_changes"][2]["change"]["after"][
            "service_account_id"
        ] = "projects/attacker/serviceAccounts/attacker@example.invalid"
        plans.append(target)
        for plan in plans:
            with self.subTest():
                with self.assertRaises(MODULE.PlanError):
                    MODULE.validate(plan, PROJECT)

    def test_rejects_update_delete_and_missing_binding(self) -> None:
        for actions in (["update"], ["delete", "create"]):
            plan = safe_plan()
            plan["resource_changes"][0]["change"]["actions"] = actions
            with self.subTest(actions=actions):
                with self.assertRaises(MODULE.PlanError):
                    MODULE.validate(plan, PROJECT)
        plan = safe_plan()
        plan["resource_changes"].pop(0)
        with self.assertRaises(MODULE.PlanError):
            MODULE.validate(plan, PROJECT)


if __name__ == "__main__":
    unittest.main()
