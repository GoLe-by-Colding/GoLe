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


def validate(plan: dict[str, Any], project_id: str) -> None:
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project_id):
        raise PlanError("expected project ID is invalid")
    resources = _changes(plan)
    allowed = set(REQUIRED_IAM) | ALLOWED_DEPENDENCIES
    if set(resources) - allowed:
        raise PlanError("operator bootstrap plan contains an unrelated resource")
    if set(REQUIRED_IAM) - set(resources):
        raise PlanError("operator bootstrap plan omits a required IAM binding")

    runtime_email = f"gole-production-runtime@{project_id}.iam.gserviceaccount.com"
    for address, item in resources.items():
        actions = _action(item)
        after = _after(item)
        if address in REQUIRED_IAM:
            if actions not in (["create"], ["no-op"]):
                raise PlanError("operator IAM action is not an exact create/no-op")
            role, scope = REQUIRED_IAM[address]
            if after.get("role") != role or after.get("member") != OPERATOR:
                raise PlanError("operator IAM principal or role changed")
            if after.get("condition") not in (None, []):
                raise PlanError("conditional operator IAM is not reviewed")
            if scope == "project" and after.get("project") not in (None, project_id):
                raise PlanError("operator IAM project changed")
            if scope == "service-account":
                service_account_id = str(after.get("service_account_id", ""))
                if not service_account_id.endswith(
                    f"/serviceAccounts/{runtime_email}"
                ) and service_account_id != runtime_email:
                    raise PlanError("operator service-account binding target changed")
        elif address == "google_project_service.iam":
            if actions != ["no-op"] or after.get("service") != "iam.googleapis.com":
                raise PlanError("IAM API dependency changed in operator bootstrap")
        elif address == "google_service_account.production_runtime":
            if (
                actions != ["no-op"]
                or after.get("account_id") != "gole-production-runtime"
                or after.get("email") != runtime_email
            ):
                raise PlanError("runtime identity dependency changed in operator bootstrap")


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
