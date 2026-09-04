#!/usr/bin/env python3
"""Fail-closed checks for an imported production Terraform plan.

The script reads ``terraform show -json`` from stdin.  It intentionally emits
only policy errors, never values from the plan, because Terraform plans may
contain sensitive application metadata.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
from typing import Any


ADDRESS_RESOURCE = "google_compute_address.gole"
INSTANCE_RESOURCE = "google_compute_instance.gole"
SECRET_RESOURCE = "google_secret_manager_secret.production_env"
BUDGET_RESOURCE = "google_billing_budget.gole_credit_guard[0]"
SNAPSHOT_POLICY_RESOURCE = "google_compute_resource_policy.daily_boot_disk_snapshots"
SNAPSHOT_ATTACHMENT_RESOURCE = (
    "google_compute_disk_resource_policy_attachment.daily_boot_disk_snapshots"
)

REQUIRED_EXISTING_RESOURCES = {
    "google_project_service.compute",
    "google_project_service.resource_manager",
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
    BUDGET_RESOURCE,
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
}
ALLOWED_CREATE_RESOURCES = REQUIRED_ADOPTION_RESOURCES | {GTS_RESOURCE}
ALLOWED_RESOURCES = REQUIRED_EXISTING_RESOURCES | REQUIRED_ADOPTION_RESOURCES | OPTIONAL_RESOURCES

# Terraform Provider 7.46 exposes these values only after the corresponding
# create finishes. Every configurable value must already be known in the saved
# plan so a reviewed identity, network, or recovery policy cannot be resolved
# to something different during apply.
ALLOWED_CREATE_UNKNOWN_PATHS: dict[str, set[tuple[str, ...]]] = {
    SNAPSHOT_POLICY_RESOURCE: {("id",), ("self_link",)},
    SNAPSHOT_ATTACHMENT_RESOURCE: {("id",)},
    "google_project_iam_member.operator_os_admin": {("etag",), ("id",)},
    "google_project_iam_member.operator_iap_tunnel": {("etag",), ("id",)},
    "google_service_account_iam_member.operator_service_account_user": {
        ("etag",),
        ("id",),
    },
    GTS_RESOURCE: {("etag",), ("id",)},
}

# Provider schemas contain computed bookkeeping fields, but an imported
# production plan must not smuggle a newly introduced privilege/network/disk
# field past checks that only know about a handful of dangerous names. Keep a
# fail-closed top-level allowlist per resource type; provider upgrades that add
# a field require an explicit review of this list before production planning.
ALLOWED_AFTER_KEYS_BY_TYPE: dict[str, set[str]] = {
    "google_project_service": {
        "deletion_policy", "disable_dependent_services", "disable_on_destroy", "id", "project",
        "service", "timeouts",
    },
    "google_service_account": {
        "account_id", "create_ignore_already_exists", "deletion_policy", "description", "disabled",
        "display_name", "email", "id", "member", "name", "project", "timeouts", "unique_id",
    },
    "google_secret_manager_secret": {
        "annotations", "create_time", "deletion_policy", "deletion_protection", "effective_annotations",
        "effective_labels", "expire_time", "id", "labels", "name", "project", "replication",
        "rotation", "secret_id", "tags", "terraform_labels", "timeouts", "topics", "ttl", "version_aliases",
        "version_destroy_ttl",
    },
    "google_secret_manager_secret_iam_member": {
        "condition", "etag", "id", "member", "project", "role", "secret_id",
    },
    "google_project_iam_custom_role": {
        "deleted", "deletion_policy", "description", "id", "name", "permissions", "project",
        "role_id", "stage", "title",
    },
    "google_project_iam_member": {
        "condition", "etag", "id", "member", "project", "role",
    },
    "google_service_account_iam_member": {
        "condition", "etag", "id", "member", "role", "service_account_id",
    },
    "google_compute_address": {
        "address", "address_id", "address_type", "creation_timestamp", "deletion_policy", "description",
        "effective_labels", "id", "ip_version", "ipv6_endpoint_type", "label_fingerprint", "labels", "name",
        "network", "network_tier",
        "prefix_length", "project", "purpose", "region", "self_link", "subnetwork", "terraform_labels",
        "timeouts", "users", "ip_collection",
    },
    "google_compute_firewall": {
        "allow", "creation_timestamp", "deletion_policy", "deny", "description", "destination_ranges",
        "direction", "disabled", "enable_logging", "id", "log_config", "name", "network", "params", "priority",
        "project", "self_link", "source_ranges", "source_service_accounts", "source_tags",
        "target_service_accounts", "target_tags", "timeouts",
    },
    "google_compute_instance": {
        "advanced_machine_features", "allow_stopping_for_update", "attached_disk", "boot_disk",
        "can_ip_forward", "confidential_instance_config", "cpu_platform", "creation_timestamp", "current_status",
        "deletion_policy", "deletion_protection", "description", "desired_status", "effective_labels", "enable_display",
        "guest_accelerator", "hostname", "id", "instance_encryption_key", "key_revocation_action_type",
        "instance_id", "label_fingerprint", "labels", "machine_type", "metadata", "metadata_fingerprint",
        "metadata_startup_script", "min_cpu_platform", "name", "network_interface",
        "network_performance_config", "params", "project", "reservation_affinity", "resource_policies",
        "scheduling", "scratch_disk", "self_link", "service_account", "shielded_instance_config",
        "tags", "tags_fingerprint", "terraform_labels", "timeouts", "workload_identity_config", "zone",
    },
    "google_compute_resource_policy": {
        "creation_timestamp", "deletion_policy", "description", "disk_consistency_group_policy",
        "group_placement_policy", "id", "instance_schedule_policy", "name", "project", "region",
        "self_link", "snapshot_schedule_policy", "timeouts", "workload_policy",
    },
    "google_compute_disk_resource_policy_attachment": {
        "deletion_policy", "disk", "id", "name", "project", "timeouts", "zone",
    },
    "google_pubsub_topic": {
        "deletion_policy", "effective_labels", "id", "ingestion_data_source_settings", "kms_key_name",
        "labels", "message_retention_duration", "message_storage_policy", "message_transforms", "name",
        "project", "schema_settings", "tags", "terraform_labels", "timeouts",
    },
    "google_pubsub_topic_iam_member": {
        "condition", "etag", "id", "member", "project", "role", "topic",
    },
    "google_pubsub_subscription": {
        "ack_deadline_seconds", "bigquery_config", "cloud_storage_config", "dead_letter_policy",
        "deletion_policy", "detached", "effective_labels", "enable_exactly_once_delivery", "enable_message_ordering",
        "expiration_policy", "filter", "id", "labels", "message_retention_duration", "name",
        "message_transforms", "project", "push_config", "retain_acked_messages", "retry_policy", "tags",
        "terraform_labels", "timeouts", "topic",
    },
    "google_pubsub_subscription_iam_member": {
        "condition", "etag", "id", "member", "project", "role", "subscription",
    },
    "google_billing_budget": {
        "all_updates_rule", "amount", "billing_account", "budget_filter", "deletion_policy",
        "display_name", "id", "name", "ownership_scope", "threshold_rules", "timeouts",
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


def _unknown_leaf_paths(value: Any, path: tuple[str, ...] = ()) -> set[tuple[str, ...]]:
    """Return the paths Terraform marks unknown, rejecting malformed masks."""

    if value is True:
        return {path}
    if value is False:
        return set()
    if isinstance(value, dict):
        paths: set[tuple[str, ...]] = set()
        for key, nested in value.items():
            if not isinstance(key, str):
                raise PlanPolicyError("plan contains a malformed after_unknown mask")
            paths.update(_unknown_leaf_paths(nested, (*path, key)))
        return paths
    if isinstance(value, list):
        paths = set()
        for index, nested in enumerate(value):
            paths.update(_unknown_leaf_paths(nested, (*path, str(index))))
        return paths
    raise PlanPolicyError("plan contains a malformed after_unknown mask")


def _assert_reviewed_after_unknown(resource: dict[str, Any], actions: list[str]) -> None:
    mask = resource.get("change", {}).get("after_unknown")
    if mask is None:
        mask = {}
    if not isinstance(mask, dict):
        raise PlanPolicyError("resource after_unknown mask is invalid")

    unknown_paths = _unknown_leaf_paths(mask)
    if actions == ["create"]:
        allowed = ALLOWED_CREATE_UNKNOWN_PATHS.get(resource["address"], set())
        if not unknown_paths.issubset(allowed):
            raise PlanPolicyError(
                "create plan contains an unresolved configurable provider value"
            )
    elif unknown_paths:
        raise PlanPolicyError("managed resource contains an unresolved after value")


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


def _assert_reviewed_provider_defaults(resource: dict[str, Any]) -> None:
    after = resource.get("change", {}).get("after")
    if not isinstance(after, dict):
        raise PlanPolicyError("managed resource is absent after apply")
    if "deletion_policy" in after and after.get("deletion_policy") != "DELETE":
        raise PlanPolicyError("provider deletion policy changed")
    if after.get("timeouts") not in (None, {}):
        raise PlanPolicyError("custom provider timeouts are not reviewed")

    resource_type = resource.get("type")
    empty_only_fields = {
        "google_compute_address": ("ip_collection",),
        "google_compute_firewall": ("params",),
        "google_compute_instance": ("params", "workload_identity_config"),
        "google_compute_resource_policy": (
            "disk_consistency_group_policy",
            "workload_policy",
        ),
        "google_pubsub_topic": ("message_transforms", "tags"),
        "google_pubsub_subscription": ("message_transforms", "tags"),
        "google_secret_manager_secret": ("tags",),
    }
    for field in empty_only_fields.get(str(resource_type), ()):
        if after.get(field) not in (None, "", [], {}):
            raise PlanPolicyError("provider field expected to remain empty")


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
    expected_project_id: str | None = None,
    allow_unset_project: bool = False,
    target_field: str | None = None,
    target_value: str | None = None,
) -> None:
    after = resources[address].get("change", {}).get("after")
    if not isinstance(after, dict):
        raise PlanPolicyError(f"IAM resource {address} is absent after apply")
    if (
        after.get("role") != role
        or after.get("member") != member
        or after.get("condition") != []
    ):
        raise PlanPolicyError(f"IAM resource {address} grants an unexpected principal or role")
    if expected_project_id is not None:
        allowed_projects = (
            {None, expected_project_id}
            if allow_unset_project
            else {expected_project_id}
        )
        if after.get("project") not in allowed_projects:
            raise PlanPolicyError(f"IAM resource {address} targets an unexpected project")
    if target_field is not None and after.get(target_field) != target_value:
        raise PlanPolicyError(f"IAM resource {address} targets an unexpected resource")


def _assert_firewall(
    resources: dict[str, dict[str, Any]],
    address: str,
    *,
    name: str,
    priority: int,
    source_ranges: list[str],
    target_tags: list[str],
    description: str,
    allow: list[dict[str, Any]] | None = None,
    deny: list[dict[str, Any]] | None = None,
) -> None:
    after = resources[address].get("change", {}).get("after")
    if not isinstance(after, dict):
        raise PlanPolicyError(f"firewall {address} is absent after apply")
    if (
        after.get("name") != name
        or after.get("description", "") != description
        or _machine_name(after.get("network")) != "default"
        or after.get("direction", "INGRESS") != "INGRESS"
        or after.get("priority", 1000) != priority
        or sorted(_list(after.get("source_ranges"))) != sorted(source_ranges)
        or sorted(_list(after.get("target_tags"))) != sorted(target_tags)
        or _list(after.get("source_tags"))
        or _list(after.get("source_service_accounts"))
        or _list(after.get("target_service_accounts"))
        or _normalized_firewall_rules(after.get("allow"))
        != _normalized_firewall_rules(allow or [])
        or _normalized_firewall_rules(after.get("deny"))
        != _normalized_firewall_rules(deny or [])
    ):
        raise PlanPolicyError(f"firewall {address} differs from the reviewed least-privilege rule")


def _normalized_firewall_rules(value: Any) -> dict[str, list[str]]:
    if not isinstance(value, list):
        raise PlanPolicyError("firewall protocol rules are invalid")
    normalized: dict[str, set[str]] = {}
    for rule in value:
        if not isinstance(rule, dict) or set(rule) != {"ports", "protocol"}:
            raise PlanPolicyError("firewall protocol rule has an unexpected field")
        protocol = rule.get("protocol")
        ports = rule.get("ports")
        if not isinstance(protocol, str) or not isinstance(ports, list) or not all(
            isinstance(port, str) for port in ports
        ):
            raise PlanPolicyError("firewall protocol rule is malformed")
        normalized.setdefault(protocol, set()).update(ports)
    return {protocol: sorted(ports) for protocol, ports in sorted(normalized.items())}


def _drop_instance_computed_bookkeeping(state: dict[str, Any]) -> None:
    """Remove only Provider 7.46 computed-only VM bookkeeping values."""

    for key in (
        "cpu_platform",
        "creation_timestamp",
        "current_status",
        "instance_id",
        "label_fingerprint",
        "metadata_fingerprint",
        "self_link",
        "tags_fingerprint",
    ):
        state.pop(key, None)

    for collection in ("attached_disk", "boot_disk", "scratch_disk"):
        for disk in _list(state.get(collection)):
            if isinstance(disk, dict):
                disk.pop("disk_encryption_key_sha256", None)

    for encryption_key in _list(state.get("instance_encryption_key")):
        if isinstance(encryption_key, dict):
            encryption_key.pop("sha256", None)

    for boot in _list(state.get("boot_disk")):
        if not isinstance(boot, dict):
            continue
        for initialize in _list(boot.get("initialize_params")):
            if not isinstance(initialize, dict):
                continue
            for key_name in (
                "source_image_encryption_key",
                "source_snapshot_encryption_key",
            ):
                for encryption_key in _list(initialize.get(key_name)):
                    if isinstance(encryption_key, dict):
                        encryption_key.pop("sha256", None)

    for interface in _list(state.get("network_interface")):
        if not isinstance(interface, dict):
            continue
        for key in ("ipv6_access_type", "name", "parent_nic_name"):
            interface.pop(key, None)


def _validate_instance_transition(
    instance_before: dict[str, Any], instance_after: dict[str, Any]
) -> None:
    """Allow only the reviewed one-time VM adoption changes."""

    normalized_before = copy.deepcopy(instance_before)
    normalized_after = copy.deepcopy(instance_after)
    _drop_instance_computed_bookkeeping(normalized_before)
    _drop_instance_computed_bookkeeping(normalized_after)

    # These target values are independently checked by _validate_instance_after.
    # Replacing them only in the comparison permits the reviewed migration while
    # leaving every other user-controlled Provider field immutable.
    for key in (
        "allow_stopping_for_update",
        "deletion_protection",
        "effective_labels",
        "labels",
        "machine_type",
        "metadata",
        "terraform_labels",
    ):
        normalized_before[key] = copy.deepcopy(normalized_after.get(key))

    before_boot = _single_block(normalized_before, "boot_disk")
    after_boot = _single_block(normalized_after, "boot_disk")
    before_boot["auto_delete"] = after_boot.get("auto_delete")

    if normalized_before != normalized_after:
        raise PlanPolicyError(
            "production VM update changes a field outside the reviewed adoption set"
        )


def _validate_instance_after(
    instance_after: dict[str, Any],
    *,
    expected_static_ip: str,
    expected_project_id: str,
    expected_startup_script_sha256: str,
) -> None:
    if instance_after.get("name") != "gole-production":
        raise PlanPolicyError("production instance name changed")
    if instance_after.get("project") not in (None, expected_project_id):
        raise PlanPolicyError("production instance project changed")
    if _machine_name(instance_after.get("zone")) != "asia-northeast3-a":
        raise PlanPolicyError("production instance zone changed")
    if instance_after.get("deletion_protection") is not True:
        raise PlanPolicyError("production deletion protection is not enabled in the plan")
    if instance_after.get("allow_stopping_for_update") is not True:
        raise PlanPolicyError("reviewed VM migration requires allow_stopping_for_update")
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
    if instance_after.get("effective_labels") != instance_after.get(
        "labels"
    ) or instance_after.get("terraform_labels") != instance_after.get("labels"):
        raise PlanPolicyError("production effective or Terraform labels changed")

    if instance_after.get("desired_status") not in (None, ""):
        raise PlanPolicyError("production desired status must remain unmanaged")
    if instance_after.get("metadata_startup_script") not in (None, ""):
        raise PlanPolicyError("standalone metadata startup script is forbidden")
    for field in (
        "advanced_machine_features",
        "confidential_instance_config",
        "guest_accelerator",
        "instance_encryption_key",
        "network_performance_config",
        "params",
        "reservation_affinity",
        "workload_identity_config",
    ):
        if instance_after.get(field) not in (None, "", [], {}):
            raise PlanPolicyError("production VM gained an unreviewed optional capability")

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
    network = _single_block(instance_after, "network_interface")
    if _machine_name(network.get("network")) != "default":
        raise PlanPolicyError("production VPC changed")
    if network.get("subnetwork") not in (None, "") and _machine_name(
        network.get("subnetwork")
    ) != "default":
        raise PlanPolicyError("production subnetwork changed")
    if network.get("subnetwork_project") not in (None, "", expected_project_id):
        raise PlanPolicyError("production subnetwork project changed")
    if network.get("network_attachment") not in (None, ""):
        raise PlanPolicyError("production network attachment is forbidden")
    if network.get("stack_type") not in (None, "", "IPV4_ONLY"):
        raise PlanPolicyError("production network stack changed")
    if network.get("nic_type") not in (None, ""):
        raise PlanPolicyError("production NIC type changed")
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
    if scheduling.get("preemptible") not in (None, False) or scheduling.get(
        "provisioning_model"
    ) not in (None, "", "STANDARD"):
        raise PlanPolicyError("production provisioning model changed")
    if any(
        scheduling.get(field) not in (None, 0)
        for field in (
            "availability_domain",
            "host_error_timeout_seconds",
            "min_node_cpus",
        )
    ):
        raise PlanPolicyError("production scheduling scalar policy changed")
    if scheduling.get("termination_time") not in (None, "") or scheduling.get(
        "instance_termination_action"
    ) not in (None, ""):
        raise PlanPolicyError("production VM gained an automatic termination policy")
    for field in (
        "local_ssd_recovery_timeout",
        "max_run_duration",
        "node_affinities",
        "on_instance_stop_action",
    ):
        if scheduling.get(field) not in (None, []):
            raise PlanPolicyError("production scheduling gained an unreviewed nested policy")
    if instance_after.get("resource_policies") not in (None, []):
        raise PlanPolicyError("production VM has an instance schedule resource policy")
    if _nat_ip(instance_after) != expected_static_ip:
        raise PlanPolicyError("plan would change the production instance NAT IP")


def _validate_budget_state(
    state: dict[str, Any],
    *,
    expected_project_id: str,
    expected_project_number: str,
    expected_billing_account_id: str,
    expected_budget_id: str,
    expected_budget_amount_krw: str,
    allow_project_recipients_disabled: bool,
) -> None:
    expected_resource_id = (
        f"billingAccounts/{expected_billing_account_id}/budgets/{expected_budget_id}"
    )
    if (
        state.get("id") != expected_resource_id
        or state.get("name") != expected_budget_id
        or state.get("billing_account") != expected_billing_account_id
        or state.get("display_name") != "GoLe production credit guard"
        or state.get("ownership_scope") != ""
    ):
        raise PlanPolicyError("billing budget identity changed")

    amount = _single_block(state, "amount")
    specified = _single_block(amount, "specified_amount")
    if amount.get("last_period_amount") is not False or specified != {
        "currency_code": "KRW",
        "nanos": 0,
        "units": expected_budget_amount_krw,
    }:
        raise PlanPolicyError("billing budget amount changed")

    budget_filter = _single_block(state, "budget_filter")
    custom_period = _single_block(budget_filter, "custom_period")
    if (
        budget_filter.get("calendar_period") != ""
        or budget_filter.get("credit_types") != []
        or budget_filter.get("credit_types_treatment") != "EXCLUDE_ALL_CREDITS"
        or budget_filter.get("labels") != {}
        or budget_filter.get("projects") != [f"projects/{expected_project_number}"]
        or budget_filter.get("resource_ancestors") != []
        or budget_filter.get("services") != []
        or budget_filter.get("subaccounts") != []
        or _single_block(custom_period, "start_date")
        != {"day": 1, "month": 9, "year": 2026}
        or _single_block(custom_period, "end_date")
        != {"day": 28, "month": 10, "year": 2026}
    ):
        raise PlanPolicyError("billing budget scope or period changed")

    threshold_rules = state.get("threshold_rules")
    expected_thresholds = [0.5, 0.75, 0.85, 0.9, 0.95, 1.0]
    if not isinstance(threshold_rules, list) or any(
        not isinstance(rule, dict)
        or not isinstance(rule.get("threshold_percent"), (int, float))
        or not isinstance(rule.get("spend_basis"), str)
        for rule in threshold_rules
    ):
        raise PlanPolicyError("billing budget thresholds are malformed")
    if sorted(
        (rule["threshold_percent"], rule["spend_basis"])
        for rule in threshold_rules
    ) != [(value, "CURRENT_SPEND") for value in expected_thresholds]:
        raise PlanPolicyError("billing budget thresholds changed")

    updates = _single_block(state, "all_updates_rule")
    recipients = updates.get("enable_project_level_recipients")
    allowed_recipients = {True, False} if allow_project_recipients_disabled else {True}
    if (
        recipients not in allowed_recipients
        or updates.get("disable_default_iam_recipients") is not False
        or updates.get("monitoring_notification_channels") != []
        or updates.get("pubsub_topic")
        != f"projects/{expected_project_id}/topics/gole-billing-budget"
        or updates.get("schema_version") != "1.0"
    ):
        raise PlanPolicyError("billing budget notification route changed")


def validate_existing_plan(
    plan: dict[str, Any], *, expected_static_ip_name: str, expected_static_ip: str,
    expected_project_id: str, expected_project_number: str,
    expected_billing_account_id: str, expected_budget_id: str,
    expected_budget_amount_krw: str, expected_startup_script_sha256: str
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
                or before.get("project") != expected_project_id
                or before.get("condition") != []
                or resource.get("change", {}).get("after") is not None
            ):
                raise PlanPolicyError("GTS bootstrap privilege revocation is not exact")
            continue
        if actions not in (["no-op"], ["update"], ["create"]):
            raise PlanPolicyError("existing-production plan contains an unreviewed action vector")
        if "delete" in actions:
            raise PlanPolicyError("existing-production plan contains a destroy or replacement")
        _assert_reviewed_after_unknown(resource, actions)
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
            and address not in {INSTANCE_RESOURCE, SECRET_RESOURCE, BUDGET_RESOURCE}
            and actions != ["no-op"]
        ):
            raise PlanPolicyError(
                "imported non-VM resource drift requires a separate reviewed migration"
            )
        if address == INSTANCE_RESOURCE and actions not in (["no-op"], ["update"]):
            raise PlanPolicyError("production VM may only be unchanged or updated in place")
        if address == SECRET_RESOURCE and actions not in (["no-op"], ["update"]):
            raise PlanPolicyError("production Secret container may only adopt labels in place")
        _assert_top_level_field_allowlist(resource)
        _assert_reviewed_provider_defaults(resource)

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
        if state.get("description") != "HE Testbed external feedback endpoint":
            raise PlanPolicyError("reserved production address description would change")
        if state.get("region") is not None and _machine_name(state.get("region")) != "asia-northeast3":
            raise PlanPolicyError("reserved production address region would change")

    instance_before, instance_after = _before_after(instance_resource)
    if instance_resource.get("change", {}).get("after_unknown") not in (None, {}):
        raise PlanPolicyError("production VM plan contains an unresolved after value")
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
    _validate_instance_transition(instance_before, instance_after)

    runtime_email = f"gole-production-runtime@{expected_project_id}.iam.gserviceaccount.com"
    _assert_exact_iam(
        resources,
        "google_secret_manager_secret_iam_member.production_env_accessor",
        role="roles/secretmanager.secretAccessor",
        member=f"serviceAccount:{runtime_email}",
        expected_project_id=expected_project_id,
        target_field="secret_id",
        target_value=f"projects/{expected_project_id}/secrets/gole-production-env",
    )
    _assert_exact_iam(
        resources,
        "google_pubsub_topic_iam_member.billing_budget_publisher",
        role="roles/pubsub.publisher",
        member="serviceAccount:billing-budget-alert@system.gserviceaccount.com",
        expected_project_id=expected_project_id,
        target_field="topic",
        target_value=f"projects/{expected_project_id}/topics/gole-billing-budget",
    )
    _assert_exact_iam(
        resources,
        "google_pubsub_subscription_iam_member.budget_relay_subscriber",
        role=f"projects/{expected_project_id}/roles/goleBudgetSubscriptionConsumer",
        member=f"serviceAccount:{runtime_email}",
        expected_project_id=expected_project_id,
        allow_unset_project=True,
        target_field="subscription",
        target_value=(
            f"projects/{expected_project_id}/subscriptions/"
            "gole-billing-budget-discord"
        ),
    )
    _assert_exact_iam(
        resources,
        "google_project_iam_member.operator_os_admin",
        role="roles/compute.osAdminLogin",
        member="user:coldingcontact@gmail.com",
        expected_project_id=expected_project_id,
    )
    _assert_exact_iam(
        resources,
        "google_project_iam_member.operator_iap_tunnel",
        role="roles/iap.tunnelResourceAccessor",
        member="user:coldingcontact@gmail.com",
        expected_project_id=expected_project_id,
    )
    _assert_exact_iam(
        resources,
        "google_service_account_iam_member.operator_service_account_user",
        role="roles/iam.serviceAccountUser",
        member="user:coldingcontact@gmail.com",
        target_field="service_account_id",
        target_value=(
            f"projects/{expected_project_id}/serviceAccounts/{runtime_email}"
        ),
    )

    _assert_firewall(
        resources,
        "google_compute_firewall.web",
        name="gole-web",
        priority=1000,
        source_ranges=["0.0.0.0/0"],
        target_tags=["gole-web"],
        description="GoLe public HTTP and HTTPS",
        allow=[{"ports": ["80", "443"], "protocol": "tcp"}],
    )
    _assert_firewall(
        resources,
        "google_compute_firewall.ssh_iap",
        name="gole-ssh-iap",
        priority=800,
        source_ranges=["35.235.240.0/20"],
        target_tags=["gole-ssh-iap"],
        description="SSH through Google IAP only",
        allow=[{"ports": ["22"], "protocol": "tcp"}],
    )
    _assert_firewall(
        resources,
        "google_compute_firewall.deny_public_admin",
        name="gole-deny-public-admin",
        priority=900,
        source_ranges=["0.0.0.0/0"],
        target_tags=["gole-ssh-iap"],
        description="",
        deny=[{"ports": ["22", "3389"], "protocol": "tcp"}],
    )

    expected_services = {
        "google_project_service.compute": "compute.googleapis.com",
        "google_project_service.resource_manager": "cloudresourcemanager.googleapis.com",
        "google_project_service.pubsub": "pubsub.googleapis.com",
        "google_project_service.billing_budgets": "billingbudgets.googleapis.com",
        "google_project_service.public_ca": "publicca.googleapis.com",
        "google_project_service.iam": "iam.googleapis.com",
        "google_project_service.secret_manager": "secretmanager.googleapis.com",
    }
    for resource_address, service in expected_services.items():
        after = resources[resource_address].get("change", {}).get("after")
        if (
            not isinstance(after, dict)
            or after.get("service") != service
            or after.get("disable_on_destroy") not in (None, False)
        ):
            raise PlanPolicyError("Google API lifecycle policy changed")

    runtime_account = resources["google_service_account.production_runtime"].get(
        "change", {}
    ).get("after")
    if not isinstance(runtime_account, dict) or (
        runtime_account.get("account_id") != "gole-production-runtime"
        or runtime_account.get("email") != runtime_email
    ):
        raise PlanPolicyError("production runtime service account identity changed")
    secret_change = resources[SECRET_RESOURCE].get("change", {})
    secret_before = secret_change.get("before")
    secret = secret_change.get("after")
    if (
        not isinstance(secret_before, dict)
        or not isinstance(secret, dict)
        or secret.get("secret_id") != "gole-production-env"
        or secret.get("project") != expected_project_id
        or secret.get("labels")
        != {"environment": "production", "managed-by": "kscold-control"}
        or secret.get("effective_labels")
        != {"environment": "production", "managed-by": "kscold-control"}
        or secret.get("replication")
        != [{"auto": [{"customer_managed_encryption": []}], "user_managed": []}]
        or secret.get("expire_time") not in (None, "")
        or secret.get("ttl") not in (None, "")
        or secret.get("rotation") != []
        or secret.get("topics") != []
        or secret.get("version_aliases") != {}
    ):
        raise PlanPolicyError("production Secret Manager container changed")
    normalized_secret_before = dict(secret_before)
    normalized_secret_before["labels"] = secret["labels"]
    normalized_secret_before["terraform_labels"] = secret.get("terraform_labels")
    if normalized_secret_before != secret:
        raise PlanPolicyError("production Secret container update is not label adoption only")

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

    if BUDGET_RESOURCE in resources:
        budget_before, budget_after = _before_after(resources[BUDGET_RESOURCE])
        _validate_budget_state(
            budget_before,
            expected_project_id=expected_project_id,
            expected_project_number=expected_project_number,
            expected_billing_account_id=expected_billing_account_id,
            expected_budget_id=expected_budget_id,
            expected_budget_amount_krw=expected_budget_amount_krw,
            allow_project_recipients_disabled=True,
        )
        _validate_budget_state(
            budget_after,
            expected_project_id=expected_project_id,
            expected_project_number=expected_project_number,
            expected_billing_account_id=expected_billing_account_id,
            expected_budget_id=expected_budget_id,
            expected_budget_amount_krw=expected_budget_amount_krw,
            allow_project_recipients_disabled=False,
        )
        normalized_budget_before = json.loads(json.dumps(budget_before))
        _single_block(normalized_budget_before, "all_updates_rule")[
            "enable_project_level_recipients"
        ] = True
        if normalized_budget_before != budget_after:
            raise PlanPolicyError(
                "billing budget update changes more than project-level recipients"
            )

    if GTS_RESOURCE in resources and _actions(resources[GTS_RESOURCE]) != ["delete"]:
        _assert_exact_iam(
            resources,
            GTS_RESOURCE,
            role="roles/publicca.externalAccountKeyCreator",
            member=f"serviceAccount:{runtime_email}",
            expected_project_id=expected_project_id,
        )

    snapshot_actions = _actions(snapshot_resource)
    attachment_actions = _actions(snapshot_attachment)
    if "delete" in snapshot_actions or "delete" in attachment_actions:
        raise PlanPolicyError("snapshot policy or attachment would be removed")
    snapshot_after = snapshot_resource.get("change", {}).get("after")
    attachment_after = snapshot_attachment.get("change", {}).get("after")
    if not isinstance(snapshot_after, dict) or not isinstance(attachment_after, dict):
        raise PlanPolicyError("snapshot policy/attachment is absent after apply")
    if (
        snapshot_after.get("name") != "gole-production-daily-snapshots"
        or snapshot_after.get("project") != expected_project_id
        or _machine_name(snapshot_after.get("region")) != "asia-northeast3"
        or snapshot_after.get("description")
        != "Daily three-day recovery points for the GoLe production boot disk"
    ):
        raise PlanPolicyError("unexpected production snapshot policy identity")
    for policy_type in (
        "disk_consistency_group_policy",
        "group_placement_policy",
        "instance_schedule_policy",
        "workload_policy",
    ):
        if snapshot_after.get(policy_type) not in (None, []):
            raise PlanPolicyError("snapshot resource contains an unreviewed policy type")
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
    if properties.get("chain_name") not in (None, ""):
        raise PlanPolicyError("snapshot chain policy changed")
    if properties.get("labels") != {
        "app": "gole",
        "environment": "production",
        "backup": "daily",
        "managed-by": "terraform",
    }:
        raise PlanPolicyError("snapshot labels changed")
    if attachment_after.get("name") != "gole-production-daily-snapshots":
        raise PlanPolicyError("boot disk snapshot attachment uses an unexpected policy")
    if attachment_after.get("project") != expected_project_id:
        raise PlanPolicyError("snapshot attachment project changed")
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
    parser.add_argument("--expected-project-number", required=True)
    parser.add_argument("--expected-billing-account-id", required=True)
    parser.add_argument("--expected-budget-id", required=True)
    parser.add_argument("--expected-budget-amount-krw", required=True)
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
            expected_project_number=args.expected_project_number,
            expected_billing_account_id=args.expected_billing_account_id,
            expected_budget_id=args.expected_budget_id,
            expected_budget_amount_krw=args.expected_budget_amount_krw,
            expected_startup_script_sha256=args.expected_startup_script_sha256,
        )
    except (json.JSONDecodeError, PlanPolicyError) as exc:
        print(f"Terraform 기존 운영 plan 검증 실패: {exc}", file=sys.stderr)
        return 1

    print("Terraform 기존 운영 plan 안전 계약을 통과했습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
