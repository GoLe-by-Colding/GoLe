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
        "type": address.split(".", 1)[0],
        "change": {
            "actions": actions,
            "before": copy.deepcopy(after) if actions == ["no-op"] else None,
            "after": after,
            "after_unknown": (
                {"condition": [], "etag": True, "id": True}
                if actions == ["create"]
                else {}
            ),
        },
    }


def safe_plan() -> dict:
    return {
        "resource_changes": [
            change(
                "google_project_iam_member.operator_os_admin",
                {
                    "condition": [],
                    "project": PROJECT,
                    "role": "roles/compute.osAdminLogin",
                    "member": "user:coldingcontact@gmail.com",
                },
                ["create"],
            ),
            change(
                "google_project_iam_member.operator_iap_tunnel",
                {
                    "condition": [],
                    "project": PROJECT,
                    "role": "roles/iap.tunnelResourceAccessor",
                    "member": "user:coldingcontact@gmail.com",
                },
                ["create"],
            ),
            change(
                "google_service_account_iam_member.operator_service_account_user",
                {
                    "condition": [],
                    "service_account_id": f"projects/{PROJECT}/serviceAccounts/{RUNTIME}",
                    "role": "roles/iam.serviceAccountUser",
                    "member": "user:coldingcontact@gmail.com",
                },
                ["create"],
            ),
            change(
                "google_project_service.iam",
                {
                    "deletion_policy": "DELETE",
                    "disable_dependent_services": None,
                    "disable_on_destroy": None,
                    "id": f"{PROJECT}/iam.googleapis.com",
                    "project": PROJECT,
                    "service": "iam.googleapis.com",
                    "timeouts": None,
                },
                ["no-op"],
            ),
            change(
                "google_service_account.production_runtime",
                {
                    "account_id": "gole-production-runtime",
                    "create_ignore_already_exists": None,
                    "deletion_policy": "DELETE",
                    "description": "Runtime identity for the GoLe production VM",
                    "disabled": False,
                    "display_name": "GoLe production runtime",
                    "email": RUNTIME,
                    "id": f"projects/{PROJECT}/serviceAccounts/{RUNTIME}",
                    "member": f"serviceAccount:{RUNTIME}",
                    "name": f"projects/{PROJECT}/serviceAccounts/{RUNTIME}",
                    "project": PROJECT,
                    "timeouts": None,
                    "unique_id": "102774382162384156627",
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
            item["change"]["after"].update({"etag": "known-etag", "id": item["address"]})
            item["change"]["before"] = copy.deepcopy(item["change"]["after"])
            item["change"]["actions"] = ["no-op"]
            item["change"]["after_unknown"] = {}
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

    def test_rejects_missing_or_foreign_iam_targets(self) -> None:
        mutations = (
            (0, "project", None),
            (1, "project", "foreign-project"),
            (
                2,
                "service_account_id",
                f"projects/foreign-project/serviceAccounts/{RUNTIME}",
            ),
        )
        for index, field, value in mutations:
            with self.subTest(index=index, field=field):
                plan = safe_plan()
                plan["resource_changes"][index]["change"]["after"][field] = value
                with self.assertRaises(MODULE.PlanError):
                    MODULE.validate(plan, PROJECT)

    def test_rejects_condition_type_and_unreviewed_fields(self) -> None:
        plans = []
        conditional = safe_plan()
        conditional["resource_changes"][0]["change"]["after"]["condition"] = [
            {"title": "unreviewed", "expression": "false"}
        ]
        plans.append(conditional)
        wrong_type = safe_plan()
        wrong_type["resource_changes"][0]["type"] = "google_project_iam_binding"
        plans.append(wrong_type)
        extra_field = safe_plan()
        extra_field["resource_changes"][0]["change"]["after"]["unreviewed"] = True
        plans.append(extra_field)
        for plan in plans:
            with self.subTest():
                with self.assertRaises(MODULE.PlanError):
                    MODULE.validate(plan, PROJECT)

    def test_rejects_unresolved_configurable_or_dependency_values(self) -> None:
        mutations = (
            (0, {"condition": [], "etag": True, "id": True, "project": True}),
            (2, {"condition": [], "etag": True, "id": True, "service_account_id": True}),
            (3, {"project": True}),
            (4, {"email": True}),
        )
        for index, mask in mutations:
            with self.subTest(index=index):
                plan = safe_plan()
                plan["resource_changes"][index]["change"]["after_unknown"] = mask
                with self.assertRaises(MODULE.PlanError):
                    MODULE.validate(plan, PROJECT)

    def test_rejects_dependency_drift_and_fake_noop(self) -> None:
        plans = []
        api_project = safe_plan()
        api_project["resource_changes"][3]["change"]["after"]["project"] = (
            "foreign-project"
        )
        plans.append(api_project)
        runtime_project = safe_plan()
        runtime_project["resource_changes"][4]["change"]["after"]["project"] = (
            "foreign-project"
        )
        plans.append(runtime_project)
        fake_noop = safe_plan()
        fake_noop["resource_changes"][3]["change"]["after"]["disable_on_destroy"] = True
        plans.append(fake_noop)
        for plan in plans:
            with self.subTest():
                with self.assertRaises(MODULE.PlanError):
                    MODULE.validate(plan, PROJECT)


if __name__ == "__main__":
    unittest.main()
