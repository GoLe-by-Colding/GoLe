from __future__ import annotations

import importlib.util
import json
import pathlib
import stat
import unittest
from types import SimpleNamespace
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[3]
MODULE_PATH = ROOT / "infra/gcp/scripts/notify-backup-failure.py"


def load_module():
    spec = importlib.util.spec_from_file_location(
        "gole_notify_backup_failure", MODULE_PATH
    )
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class NotifyBackupFailureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.module = load_module()

    def overlay(self, contents: str):
        overlay = mock.Mock()
        overlay.is_symlink.return_value = False
        overlay.is_file.return_value = True
        overlay.stat.return_value = SimpleNamespace(
            st_uid=0, st_gid=0, st_mode=stat.S_IFREG | 0o600
        )
        overlay.read_text.return_value = contents
        return overlay

    def test_reads_exact_operations_route_from_root_owned_discord_overlay(self) -> None:
        webhook = (
            "https://discord.com/api/webhooks/100000000000000002/"
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002"
        )
        overlay = self.overlay(f"DISCORD_OPERATIONS_WEBHOOK_URL={webhook}\n")
        response = mock.MagicMock()
        response.__enter__.return_value.status = 204

        with (
            mock.patch.object(self.module, "DISCORD_ENV_PATH", overlay),
            mock.patch.object(
                self.module.urllib.request, "urlopen", return_value=response
            ) as urlopen,
        ):
            self.assertEqual(self.module.main(), 0)

        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, webhook)
        payload = json.loads(request.data)
        self.assertEqual(payload["allowed_mentions"], {"parse": []})
        self.assertEqual(
            self.module.DISCORD_ENV_PATH,
            pathlib.Path("/etc/gole/discord.env"),
        )

    def test_rejects_legacy_key_duplicate_route_and_non_root_file(self) -> None:
        webhook = (
            "https://discord.com/api/webhooks/100000000000000002/"
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002"
        )
        invalid_contents = (
            f"GOLE_DISCORD_OPERATIONS_WEBHOOK_URL={webhook}\n",
            (
                f"DISCORD_OPERATIONS_WEBHOOK_URL={webhook}\n"
                f"DISCORD_OPERATIONS_WEBHOOK_URL={webhook}\n"
            ),
        )
        for contents in invalid_contents:
            with self.subTest(contents=contents.split("=", 1)[0]):
                overlay = self.overlay(contents)
                with mock.patch.object(
                    self.module, "DISCORD_ENV_PATH", overlay
                ):
                    self.assertEqual(self.module.main(), 1)

        overlay = self.overlay(f"DISCORD_OPERATIONS_WEBHOOK_URL={webhook}\n")
        overlay.stat.return_value = SimpleNamespace(
            st_uid=501, st_gid=20, st_mode=stat.S_IFREG | 0o600
        )
        with mock.patch.object(self.module, "DISCORD_ENV_PATH", overlay):
            self.assertEqual(self.module.main(), 1)


if __name__ == "__main__":
    unittest.main()
