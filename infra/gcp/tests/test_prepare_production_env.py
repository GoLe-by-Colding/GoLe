#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import pathlib
import stat
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
PREPARER = ROOT / "infra/gcp/scripts/prepare-production-env.py"
VALIDATOR_PATH = ROOT / "infra/gcp/scripts/validate-production-env.py"
PRODUCTION_FIXTURE = ROOT / "infra/gcp/tests/fixtures/production.env"
RUNBOOK = ROOT / "infra/gcp/README.md"


def load_validator():
    spec = importlib.util.spec_from_file_location("gole_env_validator", VALIDATOR_PATH)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class PrepareProductionEnvironmentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.directory = pathlib.Path(self.tempdir.name)
        self.input = self.directory / "legacy-v5.env"
        exact_keys = set(load_validator().EXACT_VALUES)
        retained = []
        for line in PRODUCTION_FIXTURE.read_text().splitlines():
            key = line.split("=", 1)[0]
            if key in exact_keys:
                continue
            retained.append(line)
        retained.insert(0, "# existing payload must remain a plain file")
        retained.extend(
            (
                "# SMTP_USERNAME=commented-private-mailbox@example.test",
                "#SMTP_PASSWORD=commented-private-app-password",
                "  # export GOLE_VERIFICATION_EMAIL_FROM=commented-private-sender@example.test",
            )
        )
        retained.extend(
            (
                "SMTP_USERNAME=legacy-private-mailbox@example.test",
                "SMTP_PASSWORD=legacy-private-app-password",
                "GOLE_VERIFICATION_EMAIL_FROM=legacy-private-sender@example.test",
            )
        )
        retained.append("CUSTOM_PRESERVED_SECRET=do-not-log-this-value")
        self.input.write_text("\n".join(retained) + "\n")
        self.original_hash = hashlib.sha256(self.input.read_bytes()).hexdigest()

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def run_preparer(self, attempted_password: str | None = None):
        command = [
            "python3",
            str(PREPARER),
            str(self.input),
            "--output-directory",
            str(self.directory),
        ]
        if attempted_password is not None:
            command.append("--smtp-password-stdin")
        return subprocess.run(
            command,
            input=None if attempted_password is None else attempted_password + "\n",
            check=False,
            capture_output=True,
            text=True,
        )

    def test_preserves_payload_scrubs_smtp_and_enforces_policy_in_0600_candidate(self) -> None:
        result = self.run_preparer()
        self.assertEqual(0, result.returncode, result.stderr)
        candidate = pathlib.Path(result.stdout.strip())
        self.assertEqual(self.directory.resolve(), candidate.parent)
        self.assertEqual(0o600, stat.S_IMODE(candidate.stat().st_mode))
        contents = candidate.read_text()
        self.assertIn("# existing payload must remain a plain file", contents)
        self.assertIn("CUSTOM_PRESERVED_SECRET=do-not-log-this-value", contents)
        for stale_secret in (
            "legacy-private-mailbox@example.test",
            "legacy-private-app-password",
            "legacy-private-sender@example.test",
            "commented-private-mailbox@example.test",
            "commented-private-app-password",
            "commented-private-sender@example.test",
        ):
            self.assertNotIn(stale_secret, contents)
            self.assertNotIn(stale_secret, result.stdout + result.stderr)
        validator = load_validator()
        values = validator.parse_env(candidate)
        validator.validate(values)
        for key, value in validator.EXACT_VALUES.items():
            self.assertEqual(value, values[key])
        self.assertEqual("", values["SMTP_USERNAME"])
        self.assertEqual("", values["SMTP_PASSWORD"])
        self.assertEqual("", values["GOLE_VERIFICATION_EMAIL_FROM"])
        self.assertEqual(self.original_hash, hashlib.sha256(self.input.read_bytes()).hexdigest())

    def test_explicitly_rejects_legacy_smtp_stdin_mode_without_leaving_candidate(self) -> None:
        password = "do-not-print-this-app-password"
        result = self.run_preparer(password)
        self.assertEqual(1, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertIn("--smtp-password-stdin is unavailable", result.stderr)
        self.assertEqual([], list(self.directory.glob("gole-env.*")))
        self.assertNotIn(password, result.stdout + result.stderr)

    def test_rejects_duplicate_input_key(self) -> None:
        with self.input.open("a") as stream:
            stream.write("MONGODB_URI=duplicate\n")
        result = self.run_preparer()
        self.assertEqual(1, result.returncode)
        self.assertEqual([], list(self.directory.glob("gole-env.*")))

    def test_runbook_prepares_stage_zero_without_collecting_an_smtp_secret(self) -> None:
        runbook = RUNBOOK.read_text(encoding="utf-8")
        self.assertIn(
            'candidate_path="$(python3 infra/gcp/scripts/prepare-production-env.py',
            runbook,
        )
        self.assertNotIn("IFS= read -r -s SMTP_APP_PASSWORD", runbook)
        self.assertNotIn("printf '%s' \"$SMTP_APP_PASSWORD\"", runbook)
        self.assertIn(
            "`--smtp-password-stdin`은 Stage 0에서 명시적으로 실패한다",
            runbook,
        )


if __name__ == "__main__":
    unittest.main()
