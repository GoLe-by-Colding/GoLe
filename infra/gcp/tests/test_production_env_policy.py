#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import runpy
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
VALIDATOR = ROOT / "infra/gcp/scripts/validate-production-env.py"
PRODUCTION_FIXTURE = ROOT / "infra/gcp/tests/fixtures/production.env"
DEVELOPMENT_FIXTURE = ROOT / "infra/gcp/tests/fixtures/development.env"


def run_validator(path: pathlib.Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(VALIDATOR), str(path)],
        check=False,
        capture_output=True,
        text=True,
    )


class ProductionEnvironmentPolicyTest(unittest.TestCase):
    def test_accepts_exact_initial_production_policy(self) -> None:
        result = run_validator(PRODUCTION_FIXTURE)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_development_fixture_without_echoing_secret_values(self) -> None:
        result = run_validator(DEVELOPMENT_FIXTURE)
        self.assertEqual(1, result.returncode)
        self.assertIn("GOLE_ENVIRONMENT must be explicitly set to production", result.stderr)
        self.assertNotIn("developer@example.test", result.stderr)

    def test_rejects_every_exact_policy_regression(self) -> None:
        original = self._read_fixture()
        for key in self._exact_policy_keys():
            with self.subTest(key=key):
                mutated = dict(original)
                mutated[key] = "unsafe-value"
                result = self._validate_mapping(mutated)
                self.assertEqual(1, result.returncode)
                self.assertIn(key, result.stderr)

    def test_rejects_missing_and_placeholder_smtp_password(self) -> None:
        original = self._read_fixture()
        for password in (
            "",
            "replace-with-google-app-password",
            " short-secret ",
            "abcdefgh ijklmno",
            "abcdefghijklmnopq",
            "abcdefghijklmno한",
        ):
            with self.subTest(password=password):
                mutated = dict(original)
                mutated["SMTP_PASSWORD"] = password
                result = self._validate_mapping(mutated)
                self.assertEqual(1, result.returncode)
                if password:
                    self.assertNotIn(password, result.stderr)

    def test_policy_validation_precedes_privileged_install(self) -> None:
        hostctl = (ROOT / "infra/gcp/scripts/gole-hostctl.sh").read_text()
        sync_start = hostctl.index("sync_secret_environment()")
        sync_end = hostctl.index("\n}\n", sync_start)
        sync_body = hostctl[sync_start:sync_end]
        validation_offset = sync_body.index("validate_production_environment")
        install_offset = sync_body.index("begin_environment_transaction")
        self.assertLess(validation_offset, install_offset)

        # The unprivileged wrapper must not read the payload or run a validator
        # from its runner-owned checkout.  It may only request the fixed
        # root-owned transaction with an exact version and request id.
        apply_script = (ROOT / "infra/gcp/scripts/apply-secret-env.sh").read_text()
        self.assertNotIn("gcloud secrets versions access", apply_script)
        self.assertNotIn("validate-production-env.py", apply_script)
        self.assertIn('sudo -n "$HOSTCTL" secret-sync "$SECRET_VERSION" "$REQUEST_ID"', apply_script)

    @staticmethod
    def _read_fixture() -> dict[str, str]:
        return dict(
            line.split("=", 1)
            for line in PRODUCTION_FIXTURE.read_text().splitlines()
            if line and not line.startswith("#")
        )

    @staticmethod
    def _exact_policy_keys() -> tuple[str, ...]:
        namespace = runpy.run_path(str(VALIDATOR), run_name="gole_production_env_policy")
        return tuple(namespace["EXACT_VALUES"])

    def _validate_mapping(self, values: dict[str, str]) -> subprocess.CompletedProcess[str]:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8") as candidate:
            candidate.write("".join(f"{key}={value}\n" for key, value in values.items()))
            candidate.flush()
            return run_validator(pathlib.Path(candidate.name))


if __name__ == "__main__":
    unittest.main()
