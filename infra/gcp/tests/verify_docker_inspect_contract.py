"""실제 Mongo 이미지의 inspect 계약과 strict 파서 거부 경계를 검증한다."""

import copy
import json
import pathlib
import re
import subprocess
import uuid


ROOT = pathlib.Path(__file__).resolve().parents[3]
SOURCE = (ROOT / "infra/gcp/scripts/gole-hostctl.sh").read_text()


def parse(function, value, service="mongo"):
    definition = re.search(rf"^{function}\(\) \{{\n.*?^\}}", SOURCE, re.M | re.S)
    assert definition, function
    return subprocess.run(
        ["bash", "-c", definition[0] + '\n' + function + ' "$1"', "test", service],
        input=json.dumps(value), text=True, capture_output=True, check=False,
    )


def main():
    image = re.search(r"  mongo:\n.*?    image: (\S+)",
                      (ROOT / "infra/gcp/docker-compose.yml").read_text(), re.S)[1]
    name = "gole-inspect-contract-" + uuid.uuid4().hex
    container = None
    try:
        # 실행·호스트 포트·운영 볼륨 없이 이미지의 실제 암묵적 마운트를 얻는다.
        container = subprocess.check_output(
            ["docker", "create", "--name", name, "--network", "none", image], text=True,
        ).strip()
        inspected = json.loads(subprocess.check_output(["docker", "inspect", container]))[0]
        networks = inspected["NetworkSettings"]["Networks"]
        mounts = inspected["Mounts"]
        old_output = subprocess.check_output([
            "docker", "inspect", "--format",
            "{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}",
            container,
        ], text=True)
        old_names = ",".join(sorted(old_output.splitlines()))
        assert old_names != ",".join(sorted(networks)), "기존 개행 오인을 재현하지 못함"
        result = parse("runtime_network_names", networks)
        assert result.returncode == 0 and result.stdout.strip() == ",".join(sorted(networks))
        for invalid in (None, [], {}, {"": {}}, {"gole_data": None}):
            assert parse("runtime_network_names", invalid).returncode != 0
        mixed = {"gole_edge": {}, "gole_agent": {}, "gole_data": {}}
        assert parse("runtime_network_names", mixed).stdout.strip() == "gole_agent,gole_data,gole_edge"

        config = next(mount for mount in mounts if mount["Destination"] == "/data/configdb")
        data = next(mount for mount in mounts if mount["Destination"] == "/data/db")
        expected = f'volume|{data["Name"]}|/data/db|true'
        result = parse("runtime_data_mounts", mounts)
        assert result.returncode == 0 and result.stdout.strip() == expected, result.stderr
        # Redis·MinIO에서는 같은 보조 마운트도 제거하지 않아 상위 정확 비교가 거부한다.
        for service in ("redis", "minio"):
            result = parse("runtime_data_mounts", mounts, service)
            assert result.returncode == 0 and "/data/configdb" in result.stdout
        for change in ({"Type": "bind"}, {"Name": "gole_wrong"}, {"Name": "A" * 64},
                       {"Driver": "nfs"}, {"RW": False}, {"RW": "true"}):
            changed = copy.deepcopy(config)
            changed.update(change)
            assert parse("runtime_data_mounts", [data, changed]).returncode != 0, change
        assert parse("runtime_data_mounts", [data, config, config]).returncode != 0
        for invalid in (None, {}, [None]):
            assert parse("runtime_data_mounts", invalid).returncode != 0
        extra = copy.deepcopy(config)
        extra["Destination"] = "/unexpected"
        result = parse("runtime_data_mounts", [data, extra])
        assert result.returncode == 0 and result.stdout.strip() != expected
        print("실제 Docker 네트워크·Mongo 암묵적 볼륨 및 변조 거부 계약 통과")
    finally:
        if container is not None:
            subprocess.run(["docker", "rm", "--force", "--volumes", container],
                           check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
