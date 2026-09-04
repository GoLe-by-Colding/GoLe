#!/usr/bin/env python3
"""Reject production Compose models that could escape the fixed Docker helper."""

from __future__ import annotations

import json
import pathlib
import re
import sys
from typing import Any


class ComposePolicyError(ValueError):
    """Rendered production Compose model violates the host privilege boundary."""


EXPECTED_SERVICES = {
    "backend",
    "budget-relay",
    "certbot",
    "frontend",
    "minio",
    "minio-init",
    "mongo",
    "mongo-init",
    "nginx",
    "redis",
    "support-agent",
}

EXPECTED_CONTAINER_NAMES = {
    "backend": "gole-backend",
    "budget-relay": "gole-budget-relay",
    "frontend": "gole-frontend",
    "minio": "gole-minio",
    "mongo": "gole-mongo",
    "nginx": "gole-nginx",
    "redis": "gole-redis",
    "support-agent": "gole-support-agent",
}

EXPECTED_IMAGES = {
    "backend": "gole/backend:local",
    "budget-relay": "gole/budget-relay:local",
    "certbot": (
        "certbot/certbot:latest@sha256:"
        "f70ad0adbb7e117f0fe42a63c553f28ea451edabc0148757b6efcd9735acaa20"
    ),
    "frontend": "gole/frontend:local",
    "minio": (
        "minio/minio@sha256:"
        "14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"
    ),
    "minio-init": (
        "minio/mc:latest@sha256:"
        "a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727"
    ),
    "mongo": (
        "mongo:7@sha256:"
        "b6421fd6d1c5ded6377b397d8983e2f82e2100dc5123332dcfda2065a472be5b"
    ),
    "mongo-init": (
        "mongo:7@sha256:"
        "b6421fd6d1c5ded6377b397d8983e2f82e2100dc5123332dcfda2065a472be5b"
    ),
    "nginx": (
        "nginx:1.29-alpine@sha256:"
        "5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
    ),
    "redis": (
        "redis:7-alpine@sha256:"
        "ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf"
    ),
    "support-agent": "gole/support-agent:local",
}

LEGACY_ADOPTION_IMAGES = {
    **EXPECTED_IMAGES,
    "minio-init": "minio/mc:latest",
    "mongo": "mongo:7",
    "mongo-init": "mongo:7",
    "nginx": "nginx:1.29-alpine",
    "redis": "redis:7-alpine",
}

EXPECTED_DOCKERFILES = {
    "backend": ("", "infra/gcp/docker/api.Dockerfile"),
    "budget-relay": ("infra/gcp/budget-relay", "Dockerfile"),
    "frontend": ("", "infra/gcp/docker/web.Dockerfile"),
    "support-agent": ("", "apps/support-agent/Dockerfile"),
}

EXPECTED_PORTS = {
    "backend": {("127.0.0.1", "8080", 8080, "tcp")},
    "frontend": {("127.0.0.1", "3000", 3000, "tcp")},
    "nginx": {(None, "80", 80, "tcp"), (None, "443", 443, "tcp")},
}

LEGACY_ADOPTION_PORTS = {
    **EXPECTED_PORTS,
    "minio": {
        ("127.0.0.1", "9000", 9000, "tcp"),
        ("127.0.0.1", "9001", 9001, "tcp"),
    },
    "mongo": {("127.0.0.1", "27017", 27017, "tcp")},
    "redis": {("127.0.0.1", "6379", 6379, "tcp")},
}

EXPECTED_SERVICE_NETWORKS = {
    "backend": {"data", "edge"},
    "budget-relay": {"edge"},
    "certbot": {"edge"},
    "frontend": {"edge"},
    "minio": {"data"},
    "minio-init": {"data"},
    "mongo": {"data"},
    "mongo-init": {"data"},
    "nginx": {"edge"},
    "redis": {"data"},
    "support-agent": {"data"},
}

EXPECTED_LOGGING = {
    "driver": "local",
    "options": {"max-file": "3", "max-size": "10m"},
}

EXPECTED_ENVIRONMENT_VALUES = {
    "backend": {
        "GOLE_AUTH_EMAIL_RECIPIENT_COOLDOWN_MAXIMUM": "1",
        "GOLE_AUTH_EMAIL_RECIPIENT_COOLDOWN_WINDOW": "PT1M",
        "GOLE_AUTH_EMAIL_RECIPIENT_DAILY_MAXIMUM": "8",
        "GOLE_AUTH_EMAIL_RECIPIENT_DAILY_WINDOW": "P1D",
        "GOLE_AUTH_EMAIL_CLIENT_BURST_MAXIMUM": "5",
        "GOLE_AUTH_EMAIL_CLIENT_BURST_WINDOW": "PT1M",
        "GOLE_AUTH_EMAIL_CLIENT_HOURLY_MAXIMUM": "30",
        "GOLE_AUTH_EMAIL_CLIENT_HOURLY_WINDOW": "PT1H",
        "GOLE_AUTH_EMAIL_GLOBAL_BURST_MAXIMUM": "60",
        "GOLE_AUTH_EMAIL_GLOBAL_BURST_WINDOW": "PT1M",
        "GOLE_AUTH_EMAIL_GLOBAL_DAILY_MAXIMUM": "300",
        "GOLE_AUTH_EMAIL_GLOBAL_DAILY_WINDOW": "P1D",
        "GOLE_AUTH_OAUTH_CLIENT_BURST_MAXIMUM": "20",
        "GOLE_AUTH_OAUTH_CLIENT_BURST_WINDOW": "PT1M",
        "GOLE_AUTH_OAUTH_CLIENT_HOURLY_MAXIMUM": "120",
        "GOLE_AUTH_OAUTH_CLIENT_HOURLY_WINDOW": "PT1H",
        "GOLE_AUTH_OAUTH_GLOBAL_BURST_MAXIMUM": "120",
        "GOLE_AUTH_OAUTH_GLOBAL_BURST_WINDOW": "PT1M",
        "GOLE_AUTH_OAUTH_GLOBAL_DAILY_MAXIMUM": "2000",
        "GOLE_AUTH_OAUTH_GLOBAL_DAILY_WINDOW": "P1D",
        "GOLE_THIRD_PARTY_PROVISION_VERSION": "2026-09-04",
        "GOLE_SELLER_IDENTITY_VERIFICATION_READY": "false",
    },
    "budget-relay": {
        "GOLE_CLOUD_BROKER_SOCKET": "/run/gole-cloud-broker/broker.sock",
        "GCP_CREDIT_AMOUNT_KRW": "395600.60",
        "GCP_CREDIT_DEADLINE": "2026-10-28",
        "GCP_FIXED_HOURLY_COST_KRW": "153.390555330",
        "GCP_HIGH_RATE_HOURLY_COST_KRW": "240.749900000",
        "GCP_RUNTIME_RATE_TRANSITION_AT": "2026-09-06T00:00:00+09:00",
        "GCP_SNAPSHOT_MAX_HOURLY_COST_KRW": "39.041010000",
        "GCP_SNAPSHOT_RETENTION_HOURS": "72",
        "GCP_MANUAL_SNAPSHOT_HOURLY_COST_KRW": "13.013670000",
        "GCP_HARD_STOP_ENABLED": "true",
        "GCP_HARD_STOP_DRY_RUN": "false",
        "GCP_HARD_STOP_BILLING_COST_KRW": "320000",
        "GCP_HARD_STOP_MIN_RESERVE_KRW": "75000",
        "GCP_HARD_STOP_ALL_IN_COST_KRW": "350000",
        "GCP_COST_GUARD_WARNING_KRW": "330000",
        "GCP_COST_GUARD_DANGER_KRW": "340000",
        "GCP_HARD_STOP_NETWORK_GIB": "30",
        "GCP_COST_GUARD_NETWORK_WARNING_GIB": "15",
        "GCP_COST_GUARD_NETWORK_DANGER_GIB": "25",
        "GCP_HARD_STOP_MAX_RUNTIME_HOURS": "1350",
        "GCP_COST_GUARD_RUNTIME_WARNING_HOURS": "1250",
        "GCP_COST_GUARD_RUNTIME_DANGER_HOURS": "1320",
        "GCP_HARD_STOP_EXPECTED_BUDGET_KRW": "370000",
        "GCP_HARD_STOP_BUDGET_DISPLAY_NAME": "GoLe production credit guard",
        "GCP_HARD_STOP_PERIOD_START": "2026-09-01",
        "GCP_VM_COST_START": "2026-09-01T19:57:05+09:00",
        "GCP_HARD_STOP_AT": "2026-10-28T01:50:00+09:00",
        "GCP_HARD_STOP_ARM_ID": "2026-09-e2-standard-2-ipv4-v3",
        "GCP_INSTANCE_ZONE": "asia-northeast3-a",
        "GCP_INSTANCE_NAME": "gole-production",
        "GCP_VAT_RATE": "0.10",
        "GCP_NETWORK_EGRESS_KRW_PER_GIB": "318.154399937",
        "GCP_STOPPED_RESOURCE_HOURLY_COST_KRW": "45.725095000",
        "GCP_COST_GUARD_INTERVAL_SECONDS": "10",
        "GCP_HARD_STOP_RETRY_SECONDS": "300",
    },
}

EXPECTED_MOUNTS = {
    "budget-relay": {
        ("volume", "budget-relay-state", "/state", False),
        (
            "bind",
            "/run/gole-cloud-broker/broker.sock",
            "/run/gole-cloud-broker/broker.sock",
            False,
        ),
        ("bind", "/sys/class/net/ens4/statistics/tx_bytes", "/host-metrics/tx_bytes", True),
        ("bind", "/proc/sys/kernel/random/boot_id", "/host-metrics/boot_id", True),
    },
    "certbot": {
        ("volume", "certbot-webroot", "/var/www/certbot", False),
        ("volume", "letsencrypt", "/etc/letsencrypt", False),
    },
    "minio": {("volume", "minio-data", "/data", False)},
    "mongo": {("volume", "mongo-data", "/data/db", False)},
    "nginx": {
        ("bind", "/etc/gole/nginx.conf", "/etc/nginx/conf.d/default.conf", True),
        ("volume", "certbot-webroot", "/var/www/certbot", True),
        ("volume", "letsencrypt", "/etc/letsencrypt", True),
    },
    "redis": {("volume", "redis-data", "/data", False)},
}

DANGEROUS_SERVICE_KEYS = {
    "cap_add",
    "cgroup",
    "cgroup_parent",
    "configs",
    "credential_spec",
    "devices",
    "device_cgroup_rules",
    "ipc",
    "isolation",
    "links",
    "network_mode",
    "pid",
    "privileged",
    "runtime",
    "secrets",
    "sysctls",
    "tmpfs",
    "ulimits",
    "userns_mode",
    "uts",
    "volumes_from",
}


def reject(condition: bool, message: str) -> None:
    if condition:
        raise ComposePolicyError(message)


def normalized_ports(service: dict[str, Any]) -> set[tuple[str | None, str, int, str]]:
    result = set()
    for port in service.get("ports", []):
        reject(not isinstance(port, dict), "a service port has invalid structure")
        result.add(
            (
                port.get("host_ip"),
                str(port.get("published")),
                int(port.get("target")),
                str(port.get("protocol", "tcp")),
            )
        )
    return result


def normalized_mounts(service: dict[str, Any]) -> set[tuple[str, str, str, bool]]:
    result = set()
    for mount in service.get("volumes", []):
        reject(not isinstance(mount, dict), "a service mount has invalid structure")
        result.add(
            (
                str(mount.get("type")),
                str(mount.get("source")),
                str(mount.get("target")),
                bool(mount.get("read_only", False)),
            )
        )
    return result


def validate(
    model: dict[str, Any],
    allow_legacy_adoption: bool = False,
    allow_missing_discord_overlay: bool = False,
    release_root: str = "/app",
) -> None:
    if release_root != "/app":
        reject(
            not release_root.startswith("/var/lib/gole/releases/")
            or len(release_root) != len("/var/lib/gole/releases/") + 40
            or any(character not in "0123456789abcdef" for character in release_root.rsplit("/", 1)[-1]),
            "immutable release root is invalid",
        )
    reject(model.get("name") != "gole", "production Compose project name must remain gole")
    services = model.get("services")
    reject(not isinstance(services, dict), "production Compose services are missing")
    accepted_service_sets = [EXPECTED_SERVICES]
    if allow_legacy_adoption:
        accepted_service_sets.extend(
            [
                EXPECTED_SERVICES - {"certbot"},
                EXPECTED_SERVICES - {"certbot", "support-agent"},
            ]
        )
    reject(
        not any(set(services) == accepted for accepted in accepted_service_sets),
        "production Compose service set changed",
    )

    for name, service in services.items():
        reject(not isinstance(service, dict), f"service {name} has invalid structure")
        dangerous = sorted(DANGEROUS_SERVICE_KEYS.intersection(service))
        reject(bool(dangerous), f"service {name} uses a forbidden host-escape setting")
        if not allow_legacy_adoption:
            reject(
                service.get("security_opt") != ["no-new-privileges:true"],
                f"service {name} must retain no-new-privileges",
            )
        expected_images = LEGACY_ADOPTION_IMAGES if allow_legacy_adoption else EXPECTED_IMAGES
        reject(service.get("image") != expected_images[name], f"service {name} image changed")
        expected_name = EXPECTED_CONTAINER_NAMES.get(name)
        reject(service.get("container_name") != expected_name, f"service {name} name changed")
        expected_ports = LEGACY_ADOPTION_PORTS if allow_legacy_adoption else EXPECTED_PORTS
        reject(
            normalized_ports(service) != expected_ports.get(name, set()),
            f"service {name} published ports changed",
        )
        service_networks = set(service.get("networks", {}))
        if allow_legacy_adoption:
            reject(service_networks != {"default"}, f"legacy service {name} network changed")
        else:
            reject(
                service_networks != EXPECTED_SERVICE_NETWORKS[name],
                f"service {name} network boundary changed",
            )
        if not allow_legacy_adoption:
            reject(
                service.get("logging") != EXPECTED_LOGGING,
                f"service {name} logging rotation changed",
            )
        reject(
            normalized_mounts(service) != EXPECTED_MOUNTS.get(name, set()),
            f"service {name} mounts changed",
        )
        environment = service.get("environment", {})
        reject(not isinstance(environment, dict), f"service {name} environment is invalid")
        protected_environment = (
            {} if allow_legacy_adoption else EXPECTED_ENVIRONMENT_VALUES.get(name, {})
        )
        for key, expected_value in protected_environment.items():
            reject(
                str(environment.get(key)) != expected_value,
                f"service {name} protected environment value changed: {key}",
            )
        build = service.get("build")
        if name in EXPECTED_DOCKERFILES:
            reject(not isinstance(build, dict), f"service {name} build definition is missing")
            reject(
                set(build).difference({"context", "dockerfile", "args"}),
                f"service {name} build uses a forbidden privilege setting",
            )
            relative_context, dockerfile = EXPECTED_DOCKERFILES[name]
            expected_context = str(pathlib.PurePosixPath(release_root) / relative_context)
            reject(
                (build.get("context"), build.get("dockerfile"))
                != (expected_context, dockerfile),
                f"service {name} build source changed",
            )
            build_arguments = build.get("args", {})
            reject(
                not isinstance(build_arguments, dict),
                f"service {name} build arguments are invalid",
            )
            if name != "frontend":
                reject(bool(build_arguments), f"service {name} unexpectedly receives build arguments")
            else:
                expected_argument_keys = {
                    "NEXT_PUBLIC_API_BASE_URL",
                    "NEXT_PUBLIC_SITE_URL",
                    "NEXT_PUBLIC_PAYMENT_MODE",
                    "NEXT_PUBLIC_PORTONE_STORE_ID",
                    "NEXT_PUBLIC_PORTONE_CHANNEL_KEY",
                    "NEXT_PUBLIC_PORTONE_CARD_CHANNEL_KEY",
                    "NEXT_PUBLIC_GA_MEASUREMENT_ID",
                    "NEXT_PUBLIC_GTM_ID",
                }
                reject(
                    set(build_arguments) != expected_argument_keys,
                    "frontend build argument set changed",
                )
                for key, expected in {
                    "NEXT_PUBLIC_API_BASE_URL": "https://gole.co.kr",
                    "NEXT_PUBLIC_SITE_URL": "https://gole.co.kr",
                    "NEXT_PUBLIC_PAYMENT_MODE": "disabled",
                }.items():
                    reject(
                        str(build_arguments.get(key)) != expected,
                        f"frontend protected build argument changed: {key}",
                    )
                for key, pattern in {
                    "NEXT_PUBLIC_GA_MEASUREMENT_ID": r"(?:|G-[A-Z0-9]+)",
                    "NEXT_PUBLIC_GTM_ID": r"(?:|GTM-[A-Z0-9]+)",
                }.items():
                    reject(
                        re.fullmatch(pattern, str(build_arguments.get(key, ""))) is None,
                        f"frontend analytics identifier is invalid: {key}",
                    )
        else:
            reject(build is not None, f"service {name} unexpectedly builds a local image")

    if not allow_legacy_adoption and not allow_missing_discord_overlay:
        backend_environment = services["backend"].get("environment", {})
        budget_environment = services["budget-relay"].get("environment", {})
        reject(
            backend_environment.get("GOLE_DISCORD_ALERTS_ENABLED") != "true",
            "production Discord alerts must remain enabled",
        )
        reject(
            backend_environment.get("DISCORD_SUPPRESS_NOTIFICATIONS") not in {"true", "false"},
            "production Discord suppression flag is invalid",
        )
        webhook_pattern = re.compile(
            r"https://(?:discord\.com|discordapp\.com)/api/webhooks/"
            r"[0-9]{1,32}/[A-Za-z0-9._-]{20,256}"
        )
        for key in (
            "DISCORD_DEPLOY_WEBHOOK_URL",
            "DISCORD_OPERATIONS_WEBHOOK_URL",
            "DISCORD_ACCOUNT_WEBHOOK_URL",
            "DISCORD_PAYMENT_WEBHOOK_URL",
            "DISCORD_SUPPORT_WEBHOOK_URL",
        ):
            reject(
                webhook_pattern.fullmatch(str(backend_environment.get(key, ""))) is None,
                f"production Discord route is missing or invalid: {key}",
            )
        reject(
            budget_environment.get("DISCORD_OPERATIONS_WEBHOOK_URL")
            != backend_environment.get("DISCORD_OPERATIONS_WEBHOOK_URL"),
            "backend and budget relay Discord operations routes must match",
        )

    minio_init = services["minio-init"].get("entrypoint")
    minio_command = "\n".join(str(item) for item in minio_init or [])
    if not allow_legacy_adoption:
        reject(
            "mc anonymous set none local/gole" not in minio_command,
            "production MinIO bucket must be explicitly private",
        )
    reject(
        "mc anonymous set download" in minio_command,
        "production MinIO bucket must not allow anonymous downloads",
    )

    volumes = model.get("volumes")
    reject(not isinstance(volumes, dict), "production Compose volumes are missing")
    reject(
        set(volumes)
        != {"budget-relay-state", "certbot-webroot", "letsencrypt", "minio-data", "mongo-data", "redis-data"},
        "production Compose volume set changed",
    )
    for volume in volumes.values():
        reject(not isinstance(volume, dict), "production volume has invalid structure")
        reject(bool(volume.get("external", False)), "external Docker volumes are forbidden")
        reject("driver_opts" in volume, "Docker volume driver options are forbidden")

    networks = model.get("networks", {})
    expected_networks = {"default"} if allow_legacy_adoption else {"data", "edge"}
    reject(set(networks) != expected_networks, "production Compose network set changed")
    for name, network in networks.items():
        reject(not isinstance(network, dict), f"Docker network {name} is invalid")
        reject(bool(network.get("external", False)), "external Docker networks are forbidden")
        reject("driver_opts" in network, "Docker network driver options are forbidden")
    if not allow_legacy_adoption:
        reject(networks["data"].get("internal") is not True, "data network must remain internal")
        reject(bool(networks["edge"].get("internal", False)), "edge network must retain egress")


def main(argv: list[str]) -> int:
    arguments = argv[1:]
    allow_legacy = False
    allow_missing_discord = False
    release_root = "/app"
    while arguments:
        argument = arguments.pop(0)
        if argument == "--allow-legacy-adoption":
            allow_legacy = True
        elif argument == "--allow-missing-discord-overlay":
            allow_missing_discord = True
        elif argument == "--release-root" and arguments:
            release_root = arguments.pop(0)
        else:
            print("invalid validator arguments", file=sys.stderr)
            return 2
    try:
        raw = sys.stdin.buffer.read(4 * 1024 * 1024 + 1)
        if not raw or len(raw) > 4 * 1024 * 1024:
            raise ComposePolicyError("rendered production Compose size is invalid")
        model = json.loads(raw)
        if not isinstance(model, dict):
            raise ComposePolicyError("rendered production Compose root is invalid")
        validate(
            model,
            allow_legacy_adoption=allow_legacy,
            allow_missing_discord_overlay=allow_missing_discord,
            release_root=release_root,
        )
    except (ComposePolicyError, json.JSONDecodeError, TypeError, ValueError) as exception:
        # Never print the JSON because rendered environment entries contain secrets.
        print(f"production Compose policy rejected: {exception}", file=sys.stderr)
        return 1
    print("Production Compose privilege policy validated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
