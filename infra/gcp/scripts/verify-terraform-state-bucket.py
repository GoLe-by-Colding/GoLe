#!/usr/bin/env python3
"""Validate the fail-closed GCS backend bucket contract from gcloud JSON."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any


class BucketPolicyError(ValueError):
    """The state bucket is not safe to use as a Terraform backend."""


def _load(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise BucketPolicyError("cannot read bucket verification metadata") from exc
    if not isinstance(value, dict):
        raise BucketPolicyError("bucket verification metadata must be an object")
    return value


def _duration_seconds(value: Any) -> int | None:
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        raw = value.removesuffix("s")
        return int(raw) if raw.isdigit() else None
    return None


def validate(
    metadata: dict[str, Any],
    iam_policy: dict[str, Any],
    *,
    bucket_name: str,
    project_number: str,
    location: str,
    principal: str,
) -> None:
    validate_identity(
        metadata,
        bucket_name=bucket_name,
        project_number=project_number,
        location=location,
    )

    iam_config = metadata.get("iamConfiguration")
    if not isinstance(iam_config, dict):
        raise BucketPolicyError("bucket IAM configuration is missing")
    ubla = iam_config.get("uniformBucketLevelAccess")
    if not isinstance(ubla, dict) or ubla.get("enabled") is not True:
        raise BucketPolicyError("uniform bucket-level access is not enabled")
    if str(iam_config.get("publicAccessPrevention", "")).lower() != "enforced":
        raise BucketPolicyError("public access prevention is not enforced")

    versioning = metadata.get("versioning")
    if not isinstance(versioning, dict) or versioning.get("enabled") is not True:
        raise BucketPolicyError("object versioning is not enabled")
    soft_delete = metadata.get("softDeletePolicy")
    if not isinstance(soft_delete, dict) or _duration_seconds(
        soft_delete.get("retentionDurationSeconds")
    ) != 604800:
        raise BucketPolicyError("soft delete must be fixed at seven days")

    lifecycle = metadata.get("lifecycle")
    rules = lifecycle.get("rule") if isinstance(lifecycle, dict) else None
    expected_rule = {
        "action": {"type": "Delete"},
        "condition": {"daysSinceNoncurrentTime": 14, "numNewerVersions": 10},
    }
    if not isinstance(rules, list) or expected_rule not in rules:
        raise BucketPolicyError("noncurrent state version lifecycle rule is missing")

    bindings = iam_policy.get("bindings")
    if not isinstance(bindings, list):
        raise BucketPolicyError("bucket IAM bindings are missing")
    allowed = any(
        isinstance(binding, dict)
        and binding.get("role") == "roles/storage.objectAdmin"
        and isinstance(binding.get("members"), list)
        and principal in binding["members"]
        for binding in bindings
    )
    if not allowed:
        raise BucketPolicyError("Terraform principal lacks bucket-scoped Storage Object Admin")


def validate_identity(
    metadata: dict[str, Any], *, bucket_name: str, project_number: str, location: str
) -> None:
    if metadata.get("name") != bucket_name:
        raise BucketPolicyError("bucket name does not match the requested backend")
    if str(metadata.get("projectNumber", "")) != project_number:
        raise BucketPolicyError("bucket belongs to a different GCP project")
    if str(metadata.get("location", "")).upper() != location.upper():
        raise BucketPolicyError("bucket is in an unexpected location")
    if str(metadata.get("storageClass", "")).upper() != "STANDARD":
        raise BucketPolicyError("state bucket must use STANDARD storage")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("metadata", type=Path)
    parser.add_argument("iam_policy", type=Path, nargs="?")
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--project-number", required=True)
    parser.add_argument("--location", required=True)
    parser.add_argument("--principal", required=True)
    parser.add_argument("--identity-only", action="store_true")
    args = parser.parse_args()
    try:
        metadata = _load(args.metadata)
        if args.identity_only:
            validate_identity(
                metadata,
                bucket_name=args.bucket,
                project_number=args.project_number,
                location=args.location,
            )
        else:
            if args.iam_policy is None:
                raise BucketPolicyError("IAM policy metadata is required")
            validate(
                metadata,
                _load(args.iam_policy),
                bucket_name=args.bucket,
                project_number=args.project_number,
                location=args.location,
                principal=args.principal,
            )
    except BucketPolicyError as exc:
        print(f"Terraform state bucket 검증 실패: {exc}", file=sys.stderr)
        return 1
    print("Terraform state bucket 보안 계약을 통과했습니다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
