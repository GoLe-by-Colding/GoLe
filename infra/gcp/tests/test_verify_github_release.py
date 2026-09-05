import importlib.util
import pathlib
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
        return {"workflow_runs": [{"head_sha": self.SHA, "conclusion": "success"}]}

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
