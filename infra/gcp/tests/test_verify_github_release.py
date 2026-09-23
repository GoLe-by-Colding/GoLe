import importlib.util
import pathlib
import json
import re
import subprocess
import sys
import unittest
from unittest import mock


SCRIPT = pathlib.Path(__file__).parents[1] / "scripts" / "verify-github-release.py"
SPEC = importlib.util.spec_from_file_location("verify_github_release", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class VerifyGithubReleaseTests(unittest.TestCase):
    SHA = "1" * 40
    CURRENT = "2" * 40

    def successful_run(self):
        return {"workflow_runs": [{"head_sha": self.SHA, "head_branch": "main",
                                   "event": "push", "status": "completed", "conclusion": "success"}]}

    def test_uses_exact_sha_without_delayed_status_filter(self):
        def response(url):
            if url.endswith("/git/ref/heads/main"):
                return {"object": {"sha": self.SHA}}
            self.assertIn(f"head_sha={self.SHA}", url)
            return {"workflow_runs": []} if "status=" in url else self.successful_run()

        with mock.patch.object(MODULE, "get_json", side_effect=response):
            MODULE.verify(self.SHA)

    def test_rejects_untrusted_or_incomplete_run_fields(self):
        for key, value in (("head_sha", self.CURRENT), ("head_branch", "dev"),
                           ("event", "pull_request"), ("status", "in_progress"),
                           ("conclusion", "failure")):
            with self.subTest(key=key):
                payload = self.successful_run()
                payload["workflow_runs"][0][key] = value
                with mock.patch.object(MODULE, "get_json", side_effect=[
                    {"object": {"sha": self.SHA}}, payload
                ]):
                    with self.assertRaisesRegex(ValueError, "successful main push CI"):
                        MODULE.verify(self.SHA)

    def test_rejects_malformed_workflow_runs(self):
        for runs in (None, {}, "success", [None], [{}]):
            with self.subTest(runs=runs):
                with mock.patch.object(MODULE, "get_json", side_effect=[
                    {"object": {"sha": self.SHA}}, {"workflow_runs": runs}
                ]):
                    with self.assertRaisesRegex(ValueError, "successful main push CI"):
                        MODULE.verify(self.SHA)

    def test_bootstrap_and_documented_entrypoint_verify_run_fields(self):
        root = SCRIPT.parents[3]
        sources = {
            "bootstrap": (SCRIPT.parent / "bootstrap-host.sh").read_text(),
            "entrypoint": (root / "infra/gcp/README.md").read_text(),
        }
        for name, source in sources.items():
            blocks = re.findall(r"<<'PY'\n(.*?)\nPY", source, re.S)
            code = next(block for block in blocks if "actions/workflows/ci.yml/runs" in block)
            for change, succeeds in (({}, True), ({"status": "in_progress"}, False),
                                     ({"conclusion": "failure"}, False), ({"head_branch": "dev"}, False),
                                     ({"event": "pull_request"}, False), ({"head_sha": self.CURRENT}, False)):
                with self.subTest(source=name, change=change):
                    payload = self.successful_run()
                    payload["workflow_runs"][0].update(change)
                    harness = """
import io, json, sys, urllib.request
from unittest.mock import patch
code, payload, sha = json.loads(sys.stdin.read())
def response(request, timeout):
    assert 'head_sha=' + sha in request.full_url
    value = {'workflow_runs': []} if 'status=' in request.full_url else payload
    return io.StringIO(json.dumps(value))
sys.argv = ['bootstrap', sha]
with patch.object(urllib.request, 'urlopen', side_effect=response):
    exec(compile(code, '<bootstrap-ci-verifier>', 'exec'))
"""
                    result = subprocess.run([sys.executable, "-c", harness],
                                            input=json.dumps([code, payload, self.SHA]),
                                            text=True, capture_output=True, timeout=10)
                    self.assertEqual(result.returncode == 0, succeeds, result.stderr)

    def test_current_release_requires_exact_main(self):
        with mock.patch.object(
            MODULE,
            "get_json",
            side_effect=[{"object": {"sha": self.CURRENT}}],
        ):
            with self.assertRaisesRegex(ValueError, "current main"):
                MODULE.verify(self.SHA)

    def test_historical_release_requires_main_ancestry_and_green_push_ci(self):
        with mock.patch.object(
            MODULE,
            "get_json",
            side_effect=[
                {"object": {"sha": self.CURRENT}},
                {"merge_base_commit": {"sha": self.SHA}},
                self.successful_run(),
            ],
        ) as fetch:
            MODULE.verify(self.SHA, historical_main=True)
        self.assertIn(f"head_sha={self.SHA}", fetch.call_args_list[-1].args[0])

    def test_historical_release_rejects_non_main_commit(self):
        with mock.patch.object(
            MODULE,
            "get_json",
            side_effect=[
                {"object": {"sha": self.CURRENT}},
                {"merge_base_commit": {"sha": "3" * 40}},
            ],
        ):
            with self.assertRaisesRegex(ValueError, "main history"):
                MODULE.verify(self.SHA, historical_main=True)

    def test_historical_release_rejects_missing_green_push_ci(self):
        with mock.patch.object(
            MODULE,
            "get_json",
            side_effect=[
                {"object": {"sha": self.CURRENT}},
                {"merge_base_commit": {"sha": self.SHA}},
                {"workflow_runs": []},
            ],
        ):
            with self.assertRaisesRegex(ValueError, "successful main push CI"):
                MODULE.verify(self.SHA, historical_main=True)


if __name__ == "__main__":
    unittest.main()
