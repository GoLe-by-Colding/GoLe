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
            if key == "SMTP_PASSWORD":
                retained.append("SMTP_PASSWORD=")
            else:
                retained.append(line)
        retained.insert(0, "# existing payload must remain a plain file")
        retained.append("CUSTOM_PRESERVED_SECRET=do-not-log-this-value")
        self.input.write_text("\n".join(retained) + "\n")
        self.original_hash = hashlib.sha256(self.input.read_bytes()).hexdigest()

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def run_preparer(self, password: str | None = None):
        command = [
            "python3",
            str(PREPARER),
            str(self.input),
            "--output-directory",
            str(self.directory),
        ]
        if password is not None:
            command.append("--smtp-password-stdin")
        return subprocess.run(
            command,
            input=None if password is None else password + "\n",
            check=False,
            capture_output=True,
            text=True,
        )

    def test_requires_valid_smtp_before_creating_candidate(self) -> None:
        result = self.run_preparer()
        self.assertEqual(1, result.returncode)
        self.assertEqual("", result.stdout)
        self.assertEqual([], list(self.directory.glob("gole-env.*")))
        self.assertNotIn("do-not-log-this-value", result.stderr)

    def test_preserves_payload_and_enforces_policy_in_0600_candidate(self) -> None:
        password = "Abcd1234Efgh5678"
        result = self.run_preparer(password)
        self.assertEqual(0, result.returncode, result.stderr)
        candidate = pathlib.Path(result.stdout.strip())
        self.assertEqual(self.directory.resolve(), candidate.parent)
        self.assertEqual(0o600, stat.S_IMODE(candidate.stat().st_mode))
        contents = candidate.read_text()
        self.assertIn("# existing payload must remain a plain file", contents)
        self.assertIn("CUSTOM_PRESERVED_SECRET=do-not-log-this-value", contents)
        self.assertNotIn(password, result.stdout + result.stderr)
        validator = load_validator()
        values = validator.parse_env(candidate)
        validator.validate(values)
        for key, value in validator.EXACT_VALUES.items():
            self.assertEqual(value, values[key])
        self.assertEqual(password, values["SMTP_PASSWORD"])
        self.assertEqual(self.original_hash, hashlib.sha256(self.input.read_bytes()).hexdigest())

    def test_rejects_non_exact_smtp_without_leaving_candidate(self) -> None:
        for password in ("abc", "Abcd 234Efgh5678", "Abcd1234Efgh56789", "Abcd1234Efgh567한"):
            with self.subTest(password=password):
                result = self.run_preparer(password)
                self.assertEqual(1, result.returncode)
                self.assertEqual([], list(self.directory.glob("gole-env.*")))
                self.assertNotIn(password, result.stdout + result.stderr)

    def test_rejects_duplicate_input_key(self) -> None:
        with self.input.open("a") as stream:
            stream.write("MONGODB_URI=duplicate\n")
        result = self.run_preparer("Abcd1234Efgh5678")
        self.assertEqual(1, result.returncode)
        self.assertEqual([], list(self.directory.glob("gole-env.*")))


if __name__ == "__main__":
    unittest.main()
