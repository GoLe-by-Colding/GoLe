#!/usr/bin/env python3
"""Validate the one-time IAM-only plan that precedes enabling OS Login.

The plan is read from stdin and only policy errors are written.  This is a
separate gate because the operator bindings must be effective and verified by
an actual IAP SSH round trip before the production VM plan enables OS Login.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from typing import Any


OPERATOR = "user:coldingcontact@gmail.com"
REQUIRED_IAM = {
    "google_project_iam_member.operator_os_admin": (
        "roles/compute.osAdminLogin",
        "project",
    ),
    "google_project_iam_member.operator_iap_tunnel": (
        "roles/iap.tunnelResourceAccessor",
        "project",
    ),
    "google_service_account_iam_member.operator_service_account_user": (
        "roles/iam.serviceAccountUser",
        "service-account",
    ),
}
ALLOWED_DEPENDENCIES = {
    "google_project_service.iam",
    "google_service_account.production_runtime",
}
EXPECTED_TYPES = {
    "google_project_iam_member.operator_os_admin": "google_project_iam_member",
    "google_project_iam_member.operator_iap_tunnel": "google_project_iam_member",
    "google_service_account_iam_member.operator_service_account_user": (
        "google_service_account_iam_member"
    ),
    "google_project_service.iam": "google_project_service",
    "google_service_account.production_runtime": "google_service_account",
}
CREATE_UNKNOWN_MASK = {"condition": [], "etag": True, "id": True}
PROJECT_IAM_CREATE_KEYS = {"condition", "member", "project", "role"}
PROJECT_IAM_NOOP_KEYS = PROJECT_IAM_CREATE_KEYS | {"etag", "id"}
SERVICE_ACCOUNT_IAM_CREATE_KEYS = {
    "condition",
    "member",
    "role",
    "service_account_id",
}
SERVICE_ACCOUNT_IAM_NOOP_KEYS = SERVICE_ACCOUNT_IAM_CREATE_KEYS | {"etag", "id"}
PROJECT_SERVICE_KEYS = {
    "deletion_policy",
    "disable_dependent_services",
    "disable_on_destroy",
    "id",
    "project",
    "service",
    "timeouts",
}
SERVICE_ACCOUNT_KEYS = {
    "account_id",
    "create_ignore_already_exists",
    "deletion_policy",
    "description",
    "disabled",
    "display_name",
    "email",
    "id",
    "member",
    "name",
    "project",
    "timeouts",
    "unique_id",
}


class PlanError(ValueError):
    pass


def _changes(plan: dict[str, Any]) -> dict[str, dict[str, Any]]:
    raw = plan.get("resource_changes")
    if not isinstance(raw, list):
        raise PlanError("plan has no resource_changes array")
    result: dict[str, dict[str, Any]] = {}
    for item in raw:
        if not isinstance(item, dict) or not isinstance(item.get("address"), str):
            raise PlanError("plan contains invalid resource metadata")
        if item["address"] in result:
            raise PlanError("plan contains a duplicate resource address")
        result[item["address"]] = item
    return result


def _action(item: dict[str, Any]) -> list[str]:
    actions = item.get("change", {}).get("actions")
    if not isinstance(actions, list) or not all(isinstance(value, str) for value in actions):
        raise PlanError("plan contains invalid actions")
    return actions


def _after(item: dict[str, Any]) -> dict[str, Any]:
    value = item.get("change", {}).get("after")
    if not isinstance(value, dict):
        raise PlanError("managed resource is absent after apply")
    return value


def _assert_exact_keys(value: dict[str, Any], expected: set[str], label: str) -> None:
    if set(value) != expected:
        raise PlanError(f"{label} contains an unreviewed provider field")


def _assert_unknown_mask(item: dict[str, Any], actions: list[str]) -> None:
    mask = item.get("change", {}).get("after_unknown")
    if actions == ["create"]:
        if mask != CREATE_UNKNOWN_MASK:
            raise PlanError("operator IAM create contains an unreviewed unknown value")
    elif mask not in (None, {}):
        raise PlanError("operator bootstrap no-op contains an unresolved after value")


def _assert_noop_transition(item: dict[str, Any]) -> None:
    change = item.get("change", {})
    if change.get("before") != change.get("after"):
        raise PlanError("operator bootstrap no-op changes managed state")


def validate(plan: dict[str, Any], project_id: str) -> None:
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project_id):
        raise PlanError("expected project ID is invalid")
    resources = _changes(plan)
    allowed = set(REQUIRED_IAM) | ALLOWED_DEPENDENCIES
    if set(resources) - allowed:
        raise PlanError("operator bootstrap plan contains an unrelated resource")
    if allowed - set(resources):
        raise PlanError("operator bootstrap plan omits a required binding or dependency")

    runtime_email = f"gole-production-runtime@{project_id}.iam.gserviceaccount.com"
    runtime_resource = f"projects/{project_id}/serviceAccounts/{runtime_email}"
    for address, item in resources.items():
        if item.get("type") != EXPECTED_TYPES[address]:
            raise PlanError("operator bootstrap resource type changed")
        actions = _action(item)
        after = _after(item)
        if address in REQUIRED_IAM:
            if actions not in (["create"], ["no-op"]):
                raise PlanError("operator IAM action is not an exact create/no-op")
            if actions == ["create"]:
                if item.get("change", {}).get("before") is not None:
                    raise PlanError("operator IAM create unexpectedly replaces existing state")
            else:
                _assert_noop_transition(item)
            _assert_unknown_mask(item, actions)
            role, scope = REQUIRED_IAM[address]
            if after.get("role") != role or after.get("member") != OPERATOR:
                raise PlanError("operator IAM principal or role changed")
            if after.get("condition") != []:
                raise PlanError("conditional operator IAM is not reviewed")
            if scope == "project":
                expected_keys = (
                    PROJECT_IAM_CREATE_KEYS
                    if actions == ["create"]
                    else PROJECT_IAM_NOOP_KEYS
                )
                _assert_exact_keys(after, expected_keys, "operator project IAM")
                if after.get("project") != project_id:
                    raise PlanError("operator IAM project changed")
            if scope == "service-account":
                expected_keys = (
                    SERVICE_ACCOUNT_IAM_CREATE_KEYS
                    if actions == ["create"]
                    else SERVICE_ACCOUNT_IAM_NOOP_KEYS
                )
                _assert_exact_keys(after, expected_keys, "operator service-account IAM")
                if after.get("service_account_id") != runtime_resource:
                    raise PlanError("operator service-account binding target changed")
        elif address == "google_project_service.iam":
            if actions != ["no-op"]:
                raise PlanError("IAM API dependency changed in operator bootstrap")
            _assert_noop_transition(item)
            _assert_unknown_mask(item, actions)
            _assert_exact_keys(after, PROJECT_SERVICE_KEYS, "IAM API dependency")
            if (
                after.get("project") != project_id
                or after.get("service") != "iam.googleapis.com"
                or after.get("id") != f"{project_id}/iam.googleapis.com"
                or after.get("deletion_policy") != "DELETE"
                or after.get("disable_dependent_services") not in (None, False)
                or after.get("disable_on_destroy") not in (None, False)
                or after.get("timeouts") not in (None, {})
            ):
                raise PlanError("IAM API dependency identity or lifecycle changed")
        elif address == "google_service_account.production_runtime":
            if actions != ["no-op"]:
                raise PlanError("runtime identity dependency changed in operator bootstrap")
            _assert_noop_transition(item)
            _assert_unknown_mask(item, actions)
            _assert_exact_keys(after, SERVICE_ACCOUNT_KEYS, "runtime identity dependency")
            if (
                after.get("project") != project_id
                or after.get("account_id") != "gole-production-runtime"
                or after.get("email") != runtime_email
                or after.get("id") != runtime_resource
                or after.get("name") != runtime_resource
                or after.get("member") != f"serviceAccount:{runtime_email}"
                or after.get("description") != "Runtime identity for the GoLe production VM"
                or after.get("display_name") != "GoLe production runtime"
                or after.get("disabled") is not False
                or after.get("deletion_policy") != "DELETE"
                or after.get("create_ignore_already_exists") not in (None, False)
                or after.get("timeouts") not in (None, {})
                or not re.fullmatch(r"[0-9]+", str(after.get("unique_id", "")))
            ):
                raise PlanError("runtime identity dependency identity or lifecycle changed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--expected-project-id", required=True)
    args = parser.parse_args()
    try:
        document = json.load(sys.stdin)
        if not isinstance(document, dict):
            raise PlanError("plan root must be an object")
        validate(document, args.expected_project_id)
    except (json.JSONDecodeError, PlanError) as exc:
        print(f"운영자 IAM bootstrap plan 검증 실패: {exc}", file=sys.stderr)
        return 1
    print("운영자 IAM bootstrap plan 안전 계약을 통과했습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
