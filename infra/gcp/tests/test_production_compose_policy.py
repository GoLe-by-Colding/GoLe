#!/usr/bin/env python3
from __future__ import annotations

import copy
import importlib.util
import json
import os
import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[3]
COMPOSE_FILE = ROOT / "infra/gcp/docker-compose.yml"
ENV_FIXTURE = ROOT / "infra/gcp/tests/fixtures/production.env"
VALIDATOR = ROOT / "infra/gcp/scripts/validate-production-compose.py"


def load_validator():
    spec = importlib.util.spec_from_file_location("gole_compose_policy", VALIDATOR)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def render_model() -> dict:
    environment = dict(os.environ)
    environment.update(
        {
            "GCP_HARD_STOP_ENABLED": "true",
            "GCP_HARD_STOP_DRY_RUN": "false",
            "GCP_HARD_STOP_PERIOD_START": "2026-09-01",
            "GCP_VM_COST_START": "2026-09-01T19:57:05+09:00",
            "GCP_HARD_STOP_AT": "2026-10-28T01:50:00+09:00",
            "GCP_HARD_STOP_ARM_ID": "2026-09-e2-standard-2-ipv4-v3",
            "GOLE_DISCORD_ALERTS_ENABLED": "true",
            "DISCORD_DEPLOY_WEBHOOK_URL": "https://discord.com/api/webhooks/100000000000000001/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000001",
            "DISCORD_OPERATIONS_WEBHOOK_URL": "https://discord.com/api/webhooks/100000000000000002/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002",
            "DISCORD_ACCOUNT_WEBHOOK_URL": "https://discord.com/api/webhooks/100000000000000003/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000003",
            "DISCORD_PAYMENT_WEBHOOK_URL": "https://discord.com/api/webhooks/100000000000000004/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000004",
            "DISCORD_SUPPORT_WEBHOOK_URL": "https://discord.com/api/webhooks/100000000000000002/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000002",
            "DISCORD_SUPPRESS_NOTIFICATIONS": "false",
        }
    )
    environment["GOLE_APP_ENV_FILE"] = str(ENV_FIXTURE)
    environment["GOLE_INFRA_ENV_FILE"] = "/dev/null"
    result = subprocess.run(
        [
            "docker",
            "compose",
            "--env-file",
            "/dev/null",
            "--env-file",
            str(ENV_FIXTURE),
            "-f",
            str(COMPOSE_FILE),
            "--profile",
            "certificate",
            "config",
            "--format",
            "json",
        ],
        check=True,
        capture_output=True,
        env=environment,
        text=True,
    )
    model = json.loads(result.stdout)
    for service in model["services"].values():
        build = service.get("build")
        if not build:
            continue
        context = pathlib.Path(build["context"])
        build["context"] = str(pathlib.Path("/app") / context.relative_to(ROOT))
    return model


class ProductionComposePolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.validator = load_validator()
        cls.model = render_model()

    def test_accepts_fixed_privilege_and_network_model(self) -> None:
        self.validator.validate(copy.deepcopy(self.model))

    def test_rejects_missing_or_invalid_discord_overlay(self) -> None:
        for key, value in (
            ("DISCORD_ACCOUNT_WEBHOOK_URL", ""),
            ("DISCORD_SUPPORT_WEBHOOK_URL", "https://attacker.test/api/webhooks/1/token"),
            ("DISCORD_SUPPRESS_NOTIFICATIONS", "maybe"),
            ("GOLE_DISCORD_ALERTS_ENABLED", "false"),
        ):
            with self.subTest(key=key):
                model = copy.deepcopy(self.model)
                model["services"]["backend"]["environment"][key] = value
                with self.assertRaises(self.validator.ComposePolicyError):
                    self.validator.validate(model)

    def test_rejects_budget_and_backend_operations_route_mismatch(self) -> None:
        model = copy.deepcopy(self.model)
        model["services"]["budget-relay"]["environment"][
            "DISCORD_OPERATIONS_WEBHOOK_URL"
        ] = "https://discord.com/api/webhooks/100000000000000099/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef_1000000099"
        with self.assertRaises(self.validator.ComposePolicyError):
            self.validator.validate(model)

    def test_empty_host_validation_can_explicitly_allow_missing_overlay(self) -> None:
        model = copy.deepcopy(self.model)
        for key in (
            "DISCORD_DEPLOY_WEBHOOK_URL",
            "DISCORD_OPERATIONS_WEBHOOK_URL",
            "DISCORD_ACCOUNT_WEBHOOK_URL",
            "DISCORD_PAYMENT_WEBHOOK_URL",
            "DISCORD_SUPPORT_WEBHOOK_URL",
        ):
            model["services"]["backend"]["environment"][key] = ""
        model["services"]["backend"]["environment"]["GOLE_DISCORD_ALERTS_ENABLED"] = "false"
        model["services"]["budget-relay"]["environment"][
            "DISCORD_OPERATIONS_WEBHOOK_URL"
        ] = ""
        self.validator.validate(model, allow_missing_discord_overlay=True)

    def test_accepts_only_the_exact_root_owned_release_build_context(self) -> None:
        release_root = "/var/lib/gole/releases/" + "a" * 40
        model = copy.deepcopy(self.model)
        for service in model["services"].values():
            build = service.get("build")
            if build:
                build["context"] = build["context"].replace("/app", release_root, 1)
        self.validator.validate(model, release_root=release_root)
        model["services"]["backend"]["build"]["context"] = "/app"
        with self.assertRaises(self.validator.ComposePolicyError):
            self.validator.validate(model, release_root=release_root)

    def test_rejects_data_service_host_publishing(self) -> None:
        for service, target in (("mongo", 27017), ("redis", 6379), ("minio", 9000)):
            with self.subTest(service=service):
                model = copy.deepcopy(self.model)
                model["services"][service]["ports"] = [
                    {
                        "host_ip": "127.0.0.1",
                        "published": str(target),
                        "target": target,
                        "protocol": "tcp",
                    }
                ]
                with self.assertRaises(self.validator.ComposePolicyError):
                    self.validator.validate(model)

    def test_rejects_edge_service_data_network_membership(self) -> None:
        for service in ("frontend", "nginx", "budget-relay"):
            with self.subTest(service=service):
                model = copy.deepcopy(self.model)
                model["services"][service]["networks"]["data"] = None
                with self.assertRaises(self.validator.ComposePolicyError):
                    self.validator.validate(model)

    def test_rejects_docker_escape_settings(self) -> None:
        mutations = []
        privileged = copy.deepcopy(self.model)
        privileged["services"]["backend"]["privileged"] = True
        mutations.append(privileged)
        socket_mount = copy.deepcopy(self.model)
        socket_mount["services"]["backend"]["volumes"] = [
            {
                "type": "bind",
                "source": "/var/run/docker.sock",
                "target": "/var/run/docker.sock",
            }
        ]
        mutations.append(socket_mount)
        host_network = copy.deepcopy(self.model)
        host_network["services"]["backend"]["network_mode"] = "host"
        mutations.append(host_network)
        for model in mutations:
            with self.assertRaises(self.validator.ComposePolicyError):
                self.validator.validate(model)

    def test_rejects_missing_or_redirected_cloud_broker_socket(self) -> None:
        for mutation in ("environment", "mount"):
            with self.subTest(mutation=mutation):
                model = copy.deepcopy(self.model)
                if mutation == "environment":
                    model["services"]["budget-relay"]["environment"][
                        "GOLE_CLOUD_BROKER_SOCKET"
                    ] = "/tmp/fake.sock"
                else:
                    model["services"]["budget-relay"]["volumes"] = [
                        mount
                        for mount in model["services"]["budget-relay"]["volumes"]
                        if mount.get("target") != "/run/gole-cloud-broker/broker.sock"
                    ]
                with self.assertRaises(self.validator.ComposePolicyError):
                    self.validator.validate(model)

    def test_rejects_relaxed_auth_or_snapshot_cost_policy(self) -> None:
        mutations = []
        auth = copy.deepcopy(self.model)
        auth["services"]["backend"]["environment"][
            "GOLE_AUTH_OAUTH_GLOBAL_DAILY_MAXIMUM"
        ] = "999999"
        mutations.append(auth)
        snapshot = copy.deepcopy(self.model)
        snapshot["services"]["budget-relay"]["environment"][
            "GCP_SNAPSHOT_MAX_HOURLY_COST_KRW"
        ] = "0"
        mutations.append(snapshot)
        fixed_cost = copy.deepcopy(self.model)
        fixed_cost["services"]["budget-relay"]["environment"][
            "GCP_FIXED_HOURLY_COST_KRW"
        ] = "1"
        mutations.append(fixed_cost)
        for model in mutations:
            with self.assertRaises(self.validator.ComposePolicyError):
                self.validator.validate(model)

    def test_rejects_missing_or_unbounded_log_rotation(self) -> None:
        for service in self.model["services"]:
            with self.subTest(service=service):
                model = copy.deepcopy(self.model)
                model["services"][service]["logging"] = {
                    "driver": "json-file",
                    "options": {"max-size": "1g"},
                }
                with self.assertRaises(self.validator.ComposePolicyError):
                    self.validator.validate(model)

    def test_rejects_public_minio_bucket_policy(self) -> None:
        model = copy.deepcopy(self.model)
        command = model["services"]["minio-init"]["entrypoint"]
        model["services"]["minio-init"]["entrypoint"] = [
            str(item).replace(
                "mc anonymous set none local/gole",
                "mc anonymous set download local/gole",
            )
            for item in command
        ]
        with self.assertRaises(self.validator.ComposePolicyError):
            self.validator.validate(model)

    def test_legacy_mode_only_accepts_known_loopback_model(self) -> None:
        model = copy.deepcopy(self.model)
        del model["services"]["support-agent"]
        del model["services"]["certbot"]
        for service in model["services"].values():
            service["networks"] = {"default": None}
        model["networks"] = {"default": {"name": "gole_default"}}
        model["services"]["mongo"]["ports"] = [
            {"host_ip": "127.0.0.1", "published": "27017", "target": 27017, "protocol": "tcp"}
        ]
        model["services"]["redis"]["ports"] = [
            {"host_ip": "127.0.0.1", "published": "6379", "target": 6379, "protocol": "tcp"}
        ]
        model["services"]["minio"]["ports"] = [
            {"host_ip": "127.0.0.1", "published": "9000", "target": 9000, "protocol": "tcp"},
            {"host_ip": "127.0.0.1", "published": "9001", "target": 9001, "protocol": "tcp"},
        ]
        for service, image in self.validator.LEGACY_ADOPTION_IMAGES.items():
            if service in model["services"]:
                model["services"][service]["image"] = image
        self.validator.validate(model, allow_legacy_adoption=True)
        model["services"]["mongo"]["ports"][0]["host_ip"] = "0.0.0.0"
        with self.assertRaises(self.validator.ComposePolicyError):
            self.validator.validate(model, allow_legacy_adoption=True)


if __name__ == "__main__":
    unittest.main()
