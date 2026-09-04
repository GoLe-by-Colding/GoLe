from __future__ import annotations

import copy
import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).parents[1] / "scripts" / "verify-terraform-state-bucket.py"
SPEC = importlib.util.spec_from_file_location("verify_terraform_state_bucket", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def metadata() -> dict:
    return {
        "name": "gole-tfstate-example",
        "projectNumber": "123456789",
        "location": "ASIA-NORTHEAST3",
        "storageClass": "STANDARD",
        "iamConfiguration": {
            "uniformBucketLevelAccess": {"enabled": True},
            "publicAccessPrevention": "enforced",
        },
        "versioning": {"enabled": True},
        "softDeletePolicy": {"retentionDurationSeconds": "604800"},
        "lifecycle": {
            "rule": [
                {
                    "action": {"type": "Delete"},
                    "condition": {"daysSinceNoncurrentTime": 14, "numNewerVersions": 10},
                }
            ]
        },
    }


def iam() -> dict:
    return {
        "bindings": [
            {
                "role": "roles/storage.objectAdmin",
                "members": ["user:operator@example.com"],
            }
        ]
    }


class StateBucketPolicyTest(unittest.TestCase):
    def validate(self, bucket: dict, policy: dict | None = None) -> None:
        MODULE.validate(
            bucket,
            iam() if policy is None else policy,
            bucket_name="gole-tfstate-example",
            project_number="123456789",
            location="asia-northeast3",
            principal="user:operator@example.com",
        )

    def test_accepts_exact_backend_contract(self) -> None:
        self.validate(metadata())

    def test_rejects_bucket_from_another_project(self) -> None:
        candidate = metadata()
        candidate["projectNumber"] = "987654321"
        with self.assertRaises(MODULE.BucketPolicyError):
            self.validate(candidate)

    def test_rejects_public_access_or_missing_ubla(self) -> None:
        candidate = metadata()
        candidate["iamConfiguration"]["publicAccessPrevention"] = "inherited"
        with self.assertRaises(MODULE.BucketPolicyError):
            self.validate(candidate)

    def test_rejects_disabled_versioning(self) -> None:
        candidate = metadata()
        candidate["versioning"]["enabled"] = False
        with self.assertRaises(MODULE.BucketPolicyError):
            self.validate(candidate)

    def test_rejects_long_or_unbounded_version_lifecycle(self) -> None:
        candidate = metadata()
        candidate["lifecycle"]["rule"][0]["condition"]["daysSinceNoncurrentTime"] = 30
        with self.assertRaises(MODULE.BucketPolicyError):
            self.validate(candidate)

    def test_rejects_broad_or_missing_operator_binding(self) -> None:
        candidate_iam = copy.deepcopy(iam())
        candidate_iam["bindings"][0]["members"] = ["allUsers"]
        with self.assertRaises(MODULE.BucketPolicyError):
            self.validate(metadata(), candidate_iam)


if __name__ == "__main__":
    unittest.main()
