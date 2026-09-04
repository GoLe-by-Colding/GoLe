#!/usr/bin/env python3
"""Fail-closed checks for an imported production Terraform plan.

The script reads ``terraform show -json`` from stdin.  It intentionally emits
only policy errors, never values from the plan, because Terraform plans may
contain sensitive application metadata.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from typing import Any


ADDRESS_RESOURCE = "google_compute_address.gole"
INSTANCE_RESOURCE = "google_compute_instance.gole"
SNAPSHOT_POLICY_RESOURCE = "google_compute_resource_policy.daily_boot_disk_snapshots"
SNAPSHOT_ATTACHMENT_RESOURCE = (
    "google_compute_disk_resource_policy_attachment.daily_boot_disk_snapshots"
)

REQUIRED_EXISTING_RESOURCES = {
    "google_project_service.compute",
    "google_project_service.pubsub",
    "google_project_service.billing_budgets",
    "google_project_service.public_ca",
    "google_project_service.iam",
    "google_project_service.secret_manager",
    "google_service_account.production_runtime",
    "google_secret_manager_secret.production_env",
    "google_secret_manager_secret_iam_member.production_env_accessor",
    "google_project_iam_custom_role.budget_subscription_consumer",
    ADDRESS_RESOURCE,
    "google_compute_firewall.web",
    "google_compute_firewall.ssh_iap",
    "google_compute_firewall.deny_public_admin",
    INSTANCE_RESOURCE,
    "google_pubsub_topic.billing_budget",
    "google_pubsub_topic_iam_member.billing_budget_publisher",
    "google_pubsub_subscription.billing_budget_discord",
    "google_pubsub_subscription_iam_member.budget_relay_subscriber",
}
REQUIRED_ADOPTION_RESOURCES = {
    SNAPSHOT_POLICY_RESOURCE,
    SNAPSHOT_ATTACHMENT_RESOURCE,
    "google_project_iam_member.operator_os_admin",
    "google_project_iam_member.operator_iap_tunnel",
    "google_service_account_iam_member.operator_service_account_user",
}
GTS_RESOURCE = "google_project_iam_member.gts_eab_creator[0]"
OPTIONAL_RESOURCES = {
    GTS_RESOURCE,
    "google_billing_budget.gole_credit_guard[0]",
}
ALLOWED_CREATE_RESOURCES = REQUIRED_ADOPTION_RESOURCES | {GTS_RESOURCE}
ALLOWED_RESOURCES = REQUIRED_EXISTING_RESOURCES | REQUIRED_ADOPTION_RESOURCES | OPTIONAL_RESOURCES

# Provider schemas contain computed bookkeeping fields, but an imported
# production plan must not smuggle a newly introduced privilege/network/disk
# field past checks that only know about a handful of dangerous names. Keep a
# fail-closed top-level allowlist per resource type; provider upgrades that add
# a field require an explicit review of this list before production planning.
ALLOWED_AFTER_KEYS_BY_TYPE: dict[str, set[str]] = {
    "google_project_service": {
        "disable_dependent_services", "disable_on_destroy", "id", "project", "service",
    },
    "google_service_account": {
        "account_id", "description", "disabled", "display_name", "email", "id", "member",
        "name", "project", "unique_id",
    },
    "google_secret_manager_secret": {
        "annotations", "create_time", "deletion_protection", "effective_annotations",
        "effective_labels", "expire_time", "id", "labels", "name", "project", "replication",
        "rotation", "secret_id", "terraform_labels", "topics", "ttl", "version_aliases",
        "version_destroy_ttl",
    },
    "google_secret_manager_secret_iam_member": {
        "condition", "etag", "id", "member", "project", "role", "secret_id",
    },
    "google_project_iam_custom_role": {
        "deleted", "description", "id", "name", "permissions", "project", "role_id", "stage",
        "title",
    },
    "google_project_iam_member": {
        "condition", "etag", "id", "member", "project", "role",
    },
    "google_service_account_iam_member": {
        "condition", "etag", "id", "member", "role", "service_account_id",
    },
    "google_compute_address": {
        "address", "address_type", "creation_timestamp", "description", "effective_labels",
        "id", "ip_version", "ipv6_endpoint_type", "labels", "name", "network", "network_tier",
        "prefix_length", "project", "purpose", "region", "self_link", "subnetwork",
        "terraform_labels", "users",
    },
    "google_compute_firewall": {
        "allow", "creation_timestamp", "deny", "description", "destination_ranges", "direction",
        "disabled", "enable_logging", "id", "log_config", "name", "network", "priority",
        "project", "self_link", "source_ranges", "source_service_accounts", "source_tags",
        "target_service_accounts", "target_tags",
    },
    "google_compute_instance": {
        "advanced_machine_features", "allow_stopping_for_update", "attached_disk", "boot_disk",
        "can_ip_forward", "confidential_instance_config", "creation_timestamp", "current_status",
        "deletion_protection", "desired_status", "effective_labels", "enable_display",
        "guest_accelerator", "hostname", "id", "instance_encryption_key", "key_revocation_action_type",
        "label_fingerprint", "labels", "machine_type", "metadata", "metadata_fingerprint",
        "metadata_startup_script", "min_cpu_platform", "name", "network_interface",
        "network_performance_config", "params", "project", "reservation_affinity", "resource_policies",
        "scheduling", "scratch_disk", "self_link", "service_account", "shielded_instance_config",
        "tags", "tags_fingerprint", "terraform_labels", "zone",
    },
    "google_compute_resource_policy": {
        "creation_timestamp", "description", "group_placement_policy", "id",
        "instance_schedule_policy", "name", "project", "region", "self_link",
        "snapshot_schedule_policy",
    },
    "google_compute_disk_resource_policy_attachment": {
        "disk", "id", "name", "project", "zone",
    },
    "google_pubsub_topic": {
        "effective_labels", "id", "ingestion_data_source_settings", "kms_key_name", "labels",
        "message_retention_duration", "message_storage_policy", "name", "project", "schema_settings",
        "terraform_labels",
    },
    "google_pubsub_topic_iam_member": {
        "condition", "etag", "id", "member", "project", "role", "topic",
    },
    "google_pubsub_subscription": {
        "ack_deadline_seconds", "bigquery_config", "cloud_storage_config", "dead_letter_policy",
        "detached", "effective_labels", "enable_exactly_once_delivery", "enable_message_ordering",
        "expiration_policy", "filter", "id", "labels", "message_retention_duration", "name",
        "project", "push_config", "retain_acked_messages", "retry_policy", "terraform_labels", "topic",
    },
    "google_pubsub_subscription_iam_member": {
        "condition", "etag", "id", "member", "project", "role", "subscription",
    },
    "google_billing_budget": {
        "all_updates_rule", "amount", "billing_account", "budget_filter", "display_name", "id", "name",
        "ownership_scope", "threshold_rules",
    },
}


class PlanPolicyError(ValueError):
    """The plan violates the existing-production safety policy."""


def _resource_changes(plan: dict[str, Any]) -> dict[str, dict[str, Any]]:
    changes = plan.get("resource_changes")
    if not isinstance(changes, list):
        raise PlanPolicyError("plan has no resource_changes array")

    indexed: dict[str, dict[str, Any]] = {}
    for change in changes:
        if not isinstance(change, dict) or not isinstance(change.get("address"), str):
            raise PlanPolicyError("plan contains an invalid resource change")
        address = change["address"]
        if address in indexed:
            raise PlanPolicyError("plan contains a duplicate resource address")
        indexed[address] = change
    return indexed


def _actions(resource: dict[str, Any]) -> list[str]:
    change = resource.get("change")
    if not isinstance(change, dict):
        raise PlanPolicyError("resource change metadata is missing")
    actions = change.get("actions")
    if not isinstance(actions, list) or not all(isinstance(item, str) for item in actions):
        raise PlanPolicyError("resource actions are invalid")
    return actions


def _assert_top_level_field_allowlist(resource: dict[str, Any]) -> None:
    resource_type = resource.get("type")
    allowed = ALLOWED_AFTER_KEYS_BY_TYPE.get(resource_type)
    if allowed is None:
        raise PlanPolicyError("plan contains a resource type without a reviewed field allowlist")
    after = resource.get("change", {}).get("after")
    if not isinstance(after, dict):
        raise PlanPolicyError("managed resource is absent after apply")
    if set(after) - allowed:
        raise PlanPolicyError("plan contains an unreviewed provider field")


def _before_after(resource: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    change = resource["change"]
    before = change.get("before")
    after = change.get("after")
    if not isinstance(before, dict) or not isinstance(after, dict):
        raise PlanPolicyError("critical imported resource is absent from before/after state")
    return before, after


def _nat_ip(instance: dict[str, Any]) -> str | None:
    interfaces = instance.get("network_interface")
    if not isinstance(interfaces, list) or len(interfaces) != 1:
        raise PlanPolicyError("production instance must have exactly one network interface")
    access = interfaces[0].get("access_config") if isinstance(interfaces[0], dict) else None
    if not isinstance(access, list) or len(access) != 1 or not isinstance(access[0], dict):
        raise PlanPolicyError("production instance must have exactly one external access config")
    value = access[0].get("nat_ip")
    return value if isinstance(value, str) else None


def _machine_name(value: Any) -> str:
    return str(value).rsplit("/", 1)[-1]


def _single_block(container: dict[str, Any], key: str) -> dict[str, Any]:
    value = container.get(key)
    if not isinstance(value, list) or len(value) != 1 or not isinstance(value[0], dict):
        raise PlanPolicyError(f"snapshot policy {key} block is invalid")
    return value[0]


def _list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def _assert_exact_iam(
    resources: dict[str, dict[str, Any]],
    address: str,
    *,
    role: str,
    member: str,
) -> None:
    after = resources[address].get("change", {}).get("after")
    if not isinstance(after, dict):
        raise PlanPolicyError(f"IAM resource {address} is absent after apply")
    if after.get("role") != role or after.get("member") != member:
        raise PlanPolicyError(f"IAM resource {address} grants an unexpected principal or role")


def _assert_firewall(
    resources: dict[str, dict[str, Any]],
    address: str,
    *,
    name: str,
    priority: int,
    source_ranges: list[str],
    target_tags: list[str],
    allow: list[dict[str, Any]] | None = None,
    deny: list[dict[str, Any]] | None = None,
) -> None:
    after = resources[address].get("change", {}).get("after")
    if not isinstance(after, dict):
        raise PlanPolicyError(f"firewall {address} is absent after apply")
    if (
        after.get("name") != name
        or _machine_name(after.get("network")) != "default"
        or after.get("direction", "INGRESS") != "INGRESS"
        or after.get("priority", 1000) != priority
        or sorted(_list(after.get("source_ranges"))) != sorted(source_ranges)
        or sorted(_list(after.get("target_tags"))) != sorted(target_tags)
        or _list(after.get("source_tags"))
        or _list(after.get("source_service_accounts"))
        or _list(after.get("target_service_accounts"))
        or _list(after.get("allow")) != (allow or [])
        or _list(after.get("deny")) != (deny or [])
    ):
        raise PlanPolicyError(f"firewall {address} differs from the reviewed least-privilege rule")


def _validate_instance_after(
    instance_after: dict[str, Any],
    *,
    expected_static_ip: str,
    expected_project_id: str,
    expected_startup_script_sha256: str,
) -> None:
    if instance_after.get("name") != "gole-production":
        raise PlanPolicyError("production instance name changed")
    if _machine_name(instance_after.get("zone")) != "asia-northeast3-a":
        raise PlanPolicyError("production instance zone changed")
    if instance_after.get("deletion_protection") is not True:
        raise PlanPolicyError("production deletion protection is not enabled in the plan")
    if _machine_name(instance_after.get("machine_type")) != "e2-standard-2":
        raise PlanPolicyError("production plan does not use the reviewed e2-standard-2 shape")
    if set(_list(instance_after.get("tags"))) != {"gole-web", "gole-ssh-iap"}:
        raise PlanPolicyError("production network tags changed")
    if instance_after.get("labels") != {
        "app": "gole",
        "environment": "production",
        "managed-by": "terraform",
    }:
        raise PlanPolicyError("production labels changed")

    metadata = instance_after.get("metadata")
    if not isinstance(metadata, dict) or set(metadata) != {"enable-oslogin", "startup-script"}:
        raise PlanPolicyError("production metadata contains an unexpected key")
    if metadata.get("enable-oslogin") != "TRUE":
        raise PlanPolicyError("production OS Login is not enabled in the plan")
    startup_script = metadata.get("startup-script")
    if not isinstance(startup_script, str) or hashlib.sha256(
        startup_script.encode("utf-8")
    ).hexdigest() != expected_startup_script_sha256:
        raise PlanPolicyError("production startup script differs from the independently reviewed hash")

    boot = _single_block(instance_after, "boot_disk")
    if boot.get("auto_delete") is not False:
        raise PlanPolicyError("production boot disk auto-delete must be disabled")
    initialize = _single_block(boot, "initialize_params")
    image = str(initialize.get("image", ""))
    if not image.endswith(
        "/projects/ubuntu-os-cloud/global/images/ubuntu-2404-noble-amd64-v20260826"
    ) and image != "projects/ubuntu-os-cloud/global/images/ubuntu-2404-noble-amd64-v20260826":
        raise PlanPolicyError("production boot image changed")
    if initialize.get("size") != 100 or _machine_name(initialize.get("type")) != "pd-balanced":
        raise PlanPolicyError("production boot disk size or type changed")

    if _list(instance_after.get("attached_disk")) or _list(instance_after.get("scratch_disk")):
        raise PlanPolicyError("production instance gained an unreviewed disk")
    if instance_after.get("can_ip_forward") not in (None, False):
        raise PlanPolicyError("production IP forwarding was enabled")
    if instance_after.get("enable_display") not in (None, False):
        raise PlanPolicyError("production display device was enabled")
    if instance_after.get("hostname") not in (None, ""):
        raise PlanPolicyError("production hostname override is forbidden")
    network = instance_after["network_interface"][0]
    if _machine_name(network.get("network")) != "default":
        raise PlanPolicyError("production VPC changed")
    if _list(network.get("alias_ip_range")) or _list(network.get("ipv6_access_config")):
        raise PlanPolicyError("production network interface gained an unreviewed address")

    account = _single_block(instance_after, "service_account")
    if account.get("email") != f"gole-production-runtime@{expected_project_id}.iam.gserviceaccount.com":
        raise PlanPolicyError("production runtime service account changed")
    scopes = set(_list(account.get("scopes")))
    if scopes not in ({"cloud-platform"}, {"https://www.googleapis.com/auth/cloud-platform"}):
        raise PlanPolicyError("production OAuth scopes changed")

    shielded = _single_block(instance_after, "shielded_instance_config")
    if any(
        shielded.get(key) is not True
        for key in ("enable_secure_boot", "enable_vtpm", "enable_integrity_monitoring")
    ):
        raise PlanPolicyError("Shielded VM protections changed")
    scheduling = _single_block(instance_after, "scheduling")
    if scheduling.get("automatic_restart") is not True or scheduling.get(
        "on_host_maintenance"
    ) != "MIGRATE":
        raise PlanPolicyError("production scheduling policy changed")
    if instance_after.get("resource_policies") not in (None, []):
        raise PlanPolicyError("production VM has an instance schedule resource policy")
    if _nat_ip(instance_after) != expected_static_ip:
        raise PlanPolicyError("plan would change the production instance NAT IP")


def validate_existing_plan(
    plan: dict[str, Any], *, expected_static_ip_name: str, expected_static_ip: str,
    expected_project_id: str, expected_startup_script_sha256: str
) -> None:
    resources = _resource_changes(plan)

    unexpected = sorted(set(resources) - ALLOWED_RESOURCES)
    if unexpected:
        raise PlanPolicyError("existing-production plan contains an unreviewed resource address")
    missing = sorted((REQUIRED_EXISTING_RESOURCES | REQUIRED_ADOPTION_RESOURCES) - set(resources))
    if missing:
        raise PlanPolicyError("existing-production plan omits a required managed resource")

    for address, resource in resources.items():
        actions = _actions(resource)
        if address == GTS_RESOURCE and actions == ["delete"]:
            before = resource.get("change", {}).get("before")
            runtime_email = (
                f"gole-production-runtime@{expected_project_id}.iam.gserviceaccount.com"
            )
            if (
                not isinstance(before, dict)
                or before.get("role") != "roles/publicca.externalAccountKeyCreator"
                or before.get("member") != f"serviceAccount:{runtime_email}"
                or resource.get("change", {}).get("after") is not None
            ):
                raise PlanPolicyError("GTS bootstrap privilege revocation is not exact")
            continue
        if actions not in (["no-op"], ["update"], ["create"]):
            raise PlanPolicyError("existing-production plan contains an unreviewed action vector")
        if "delete" in actions:
            raise PlanPolicyError("existing-production plan contains a destroy or replacement")
        if resource.get("type") == "google_secret_manager_secret_version" or resource[
            "address"
        ].startswith("google_secret_manager_secret_version."):
            raise PlanPolicyError("Secret Manager payload/version must never enter Terraform state")
        if "create" in actions and address not in ALLOWED_CREATE_RESOURCES:
            raise PlanPolicyError("an existing resource was not imported before planning")
        if address in REQUIRED_ADOPTION_RESOURCES and actions not in (["create"], ["no-op"]):
            raise PlanPolicyError(
                "reviewed adoption resource must be an exact create or an already-managed no-op"
            )
        if (
            address in REQUIRED_EXISTING_RESOURCES
            and address != INSTANCE_RESOURCE
            and actions != ["no-op"]
        ):
            raise PlanPolicyError(
                "imported non-VM resource drift requires a separate reviewed migration"
            )
        if address == INSTANCE_RESOURCE and actions not in (["no-op"], ["update"]):
            raise PlanPolicyError("production VM may only be unchanged or updated in place")
        _assert_top_level_field_allowlist(resource)

    try:
        address_resource = resources[ADDRESS_RESOURCE]
        instance_resource = resources[INSTANCE_RESOURCE]
        snapshot_resource = resources[SNAPSHOT_POLICY_RESOURCE]
        snapshot_attachment = resources[SNAPSHOT_ATTACHMENT_RESOURCE]
    except KeyError as exc:
        raise PlanPolicyError("critical imported resource is missing from the plan") from exc

    for resource in (address_resource, instance_resource):
        actions = _actions(resource)
        if "create" in actions:
            raise PlanPolicyError("critical production resource was not imported before planning")

    address_before, address_after = _before_after(address_resource)
    for state in (address_before, address_after):
        if state.get("name") != expected_static_ip_name:
            raise PlanPolicyError("reserved address resource name would not be preserved")
        if state.get("address") != expected_static_ip:
            raise PlanPolicyError("reserved production IP would not be preserved")
        if state.get("network_tier") != "STANDARD":
            raise PlanPolicyError("reserved production address network tier would change")
        if state.get("region") is not None and _machine_name(state.get("region")) != "asia-northeast3":
            raise PlanPolicyError("reserved production address region would change")

    instance_before, instance_after = _before_after(instance_resource)
    if _nat_ip(instance_before) != expected_static_ip:
        raise PlanPolicyError("current instance NAT IP does not match the reserved production IP")
    if _nat_ip(instance_after) != expected_static_ip:
        raise PlanPolicyError("plan would change the production instance NAT IP")
    for state in (instance_before, instance_after):
        access = state["network_interface"][0]["access_config"][0]
        if access.get("network_tier") != "STANDARD":
            raise PlanPolicyError("production instance network tier would change")
    _validate_instance_after(
        instance_after,
        expected_static_ip=expected_static_ip,
        expected_project_id=expected_project_id,
        expected_startup_script_sha256=expected_startup_script_sha256,
    )

    runtime_email = f"gole-production-runtime@{expected_project_id}.iam.gserviceaccount.com"
    _assert_exact_iam(
        resources,
        "google_secret_manager_secret_iam_member.production_env_accessor",
        role="roles/secretmanager.secretAccessor",
        member=f"serviceAccount:{runtime_email}",
    )
    _assert_exact_iam(
        resources,
        "google_pubsub_topic_iam_member.billing_budget_publisher",
        role="roles/pubsub.publisher",
        member="serviceAccount:billing-budget-alert@system.gserviceaccount.com",
    )
    _assert_exact_iam(
        resources,
        "google_pubsub_subscription_iam_member.budget_relay_subscriber",
        role=f"projects/{expected_project_id}/roles/goleBudgetSubscriptionConsumer",
        member=f"serviceAccount:{runtime_email}",
    )
    _assert_exact_iam(
        resources,
        "google_project_iam_member.operator_os_admin",
        role="roles/compute.osAdminLogin",
        member="user:coldingcontact@gmail.com",
    )
    _assert_exact_iam(
        resources,
        "google_project_iam_member.operator_iap_tunnel",
        role="roles/iap.tunnelResourceAccessor",
        member="user:coldingcontact@gmail.com",
    )
    _assert_exact_iam(
        resources,
        "google_service_account_iam_member.operator_service_account_user",
        role="roles/iam.serviceAccountUser",
        member="user:coldingcontact@gmail.com",
    )

    _assert_firewall(
        resources,
        "google_compute_firewall.web",
        name="gole-web",
        priority=1000,
        source_ranges=["0.0.0.0/0"],
        target_tags=["gole-web"],
        allow=[{"ports": ["80", "443"], "protocol": "tcp"}],
    )
    _assert_firewall(
        resources,
        "google_compute_firewall.ssh_iap",
        name="gole-ssh-iap",
        priority=800,
        source_ranges=["35.235.240.0/20"],
        target_tags=["gole-ssh-iap"],
        allow=[{"ports": ["22"], "protocol": "tcp"}],
    )
    _assert_firewall(
        resources,
        "google_compute_firewall.deny_public_admin",
        name="gole-deny-public-admin",
        priority=900,
        source_ranges=["0.0.0.0/0"],
        target_tags=["gole-ssh-iap"],
        deny=[{"ports": ["22", "3389"], "protocol": "tcp"}],
    )

    expected_services = {
        "google_project_service.compute": "compute.googleapis.com",
        "google_project_service.pubsub": "pubsub.googleapis.com",
        "google_project_service.billing_budgets": "billingbudgets.googleapis.com",
        "google_project_service.public_ca": "publicca.googleapis.com",
        "google_project_service.iam": "iam.googleapis.com",
        "google_project_service.secret_manager": "secretmanager.googleapis.com",
    }
    for resource_address, service in expected_services.items():
        after = resources[resource_address].get("change", {}).get("after")
        if not isinstance(after, dict) or after.get("service") != service or after.get(
            "disable_on_destroy"
        ) is not False:
            raise PlanPolicyError("Google API lifecycle policy changed")

    runtime_account = resources["google_service_account.production_runtime"].get(
        "change", {}
    ).get("after")
    if not isinstance(runtime_account, dict) or (
        runtime_account.get("account_id") != "gole-production-runtime"
        or runtime_account.get("email") != runtime_email
    ):
        raise PlanPolicyError("production runtime service account identity changed")
    secret = resources["google_secret_manager_secret.production_env"].get("change", {}).get(
        "after"
    )
    if not isinstance(secret, dict) or secret.get("secret_id") != "gole-production-env":
        raise PlanPolicyError("production Secret Manager container changed")

    expected_custom_roles = {
        "google_project_iam_custom_role.budget_subscription_consumer": (
            "goleBudgetSubscriptionConsumer",
            ["pubsub.subscriptions.consume"],
        ),
    }
    for resource_address, (role_id, permissions) in expected_custom_roles.items():
        after = resources[resource_address].get("change", {}).get("after")
        if (
            not isinstance(after, dict)
            or after.get("role_id") != role_id
            or sorted(_list(after.get("permissions"))) != permissions
            or after.get("stage") != "GA"
        ):
            raise PlanPolicyError("custom IAM role permissions changed")

    topic = resources["google_pubsub_topic.billing_budget"].get("change", {}).get("after")
    subscription = resources["google_pubsub_subscription.billing_budget_discord"].get(
        "change", {}
    ).get("after")
    if not isinstance(topic, dict) or topic.get("name") != "gole-billing-budget":
        raise PlanPolicyError("billing Pub/Sub topic changed")
    if (
        not isinstance(subscription, dict)
        or subscription.get("name") != "gole-billing-budget-discord"
        or subscription.get("ack_deadline_seconds") != 60
        or subscription.get("message_retention_duration") != "604800s"
        or _machine_name(subscription.get("topic")) != "gole-billing-budget"
    ):
        raise PlanPolicyError("billing Pub/Sub subscription changed")

    if GTS_RESOURCE in resources and _actions(resources[GTS_RESOURCE]) != ["delete"]:
        _assert_exact_iam(
            resources,
            GTS_RESOURCE,
            role="roles/publicca.externalAccountKeyCreator",
            member=f"serviceAccount:{runtime_email}",
        )

    snapshot_actions = _actions(snapshot_resource)
    attachment_actions = _actions(snapshot_attachment)
    if "delete" in snapshot_actions or "delete" in attachment_actions:
        raise PlanPolicyError("snapshot policy or attachment would be removed")
    snapshot_after = snapshot_resource.get("change", {}).get("after")
    attachment_after = snapshot_attachment.get("change", {}).get("after")
    if not isinstance(snapshot_after, dict) or not isinstance(attachment_after, dict):
        raise PlanPolicyError("snapshot policy/attachment is absent after apply")
    if snapshot_after.get("name") != "gole-production-daily-snapshots":
        raise PlanPolicyError("unexpected production snapshot policy name")
    if snapshot_after.get("instance_schedule_policy") not in (None, []):
        raise PlanPolicyError("snapshot resource must not contain an instance schedule")
    policy = _single_block(snapshot_after, "snapshot_schedule_policy")
    schedule = _single_block(policy, "schedule")
    daily = _single_block(schedule, "daily_schedule")
    retention = _single_block(policy, "retention_policy")
    properties = _single_block(policy, "snapshot_properties")
    if daily != {"days_in_cycle": 1, "start_time": "20:00"}:
        raise PlanPolicyError("snapshot schedule must remain daily at 20:00 UTC")
    if retention != {
        "max_retention_days": 3,
        "on_source_disk_delete": "APPLY_RETENTION_POLICY",
    }:
        raise PlanPolicyError("snapshot retention policy changed")
    if properties.get("guest_flush") is not False or properties.get(
        "storage_locations"
    ) != ["asia-northeast3"]:
        raise PlanPolicyError("snapshot consistency/location policy changed")
    if properties.get("labels") != {
        "app": "gole",
        "environment": "production",
        "backup": "daily",
        "managed-by": "terraform",
    }:
        raise PlanPolicyError("snapshot labels changed")
    if attachment_after.get("name") != "gole-production-daily-snapshots":
        raise PlanPolicyError("boot disk snapshot attachment uses an unexpected policy")
    if attachment_after.get("disk") != "gole-production":
        raise PlanPolicyError("snapshot policy is not attached to the production boot disk")
    if _machine_name(attachment_after.get("zone")) != "asia-northeast3-a":
        raise PlanPolicyError("snapshot attachment zone changed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--existing", action="store_true", required=True)
    parser.add_argument("--expected-static-ip-name", required=True)
    parser.add_argument("--expected-static-ip", required=True)
    parser.add_argument("--expected-project-id", required=True)
    parser.add_argument("--expected-startup-script-sha256", required=True)
    args = parser.parse_args()

    try:
        document = json.load(sys.stdin)
        if not isinstance(document, dict):
            raise PlanPolicyError("plan root must be an object")
        validate_existing_plan(
            document,
            expected_static_ip_name=args.expected_static_ip_name,
            expected_static_ip=args.expected_static_ip,
            expected_project_id=args.expected_project_id,
            expected_startup_script_sha256=args.expected_startup_script_sha256,
        )
    except (json.JSONDecodeError, PlanPolicyError) as exc:
        print(f"Terraform 기존 운영 plan 검증 실패: {exc}", file=sys.stderr)
        return 1

    print("Terraform 기존 운영 plan 안전 계약을 통과했습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
