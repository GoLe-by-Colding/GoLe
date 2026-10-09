"""실제 이미지를 운영 자원 제한으로 기동하고 Docker readiness와 문의 RPC를 검증한다."""

import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid


ROOT = Path(__file__).resolve().parents[3]
FIXTURE = ROOT / "infra/gcp/tests/fixtures/production.env"


def docker(*args, **kwargs):
    return subprocess.run(
        ["docker", *args], check=True, text=True, capture_output=True, timeout=30, **kwargs
    ).stdout


def verify(image):
    # 실제 키나 호스트 환경 오버라이드를 읽지 않고 Compose의 운영 기본값을 사용한다.
    environment = {key: os.environ[key] for key in ("PATH", "HOME", "DOCKER_HOST", "DOCKER_CONTEXT")
                   if key in os.environ}
    environment.update(GOLE_APP_ENV_FILE=str(FIXTURE), GOLE_INFRA_ENV_FILE=str(FIXTURE))
    model = json.loads(docker(
        "compose", "--env-file", str(FIXTURE), "-f", str(ROOT / "infra/gcp/docker-compose.yml"),
        "config", "--format", "json", env=environment,
    ))
    service = model["services"]["support-agent"]
    probe = service["healthcheck"]
    image_probe = json.loads(docker("image", "inspect", image))[0]["Config"]["Healthcheck"]
    expected_probe = {"Test": probe["test"], "Retries": probe["retries"]}
    for source, target in (("timeout", "Timeout"), ("interval", "Interval"), ("start_period", "StartPeriod")):
        expected_probe[target] = int(probe[source].removesuffix("s")) * 1_000_000_000
    for key, value in expected_probe.items():
        assert image_probe[key] == value, f"이미지와 운영 Compose의 healthcheck 불일치: {key}"

    name = "gole-agent-readiness-test-" + uuid.uuid4().hex[:12]
    try:
        args = ["create", "--name", name, "--network", "none", "--cpus", str(service["cpus"]),
                "--memory", str(service["mem_limit"])]
        for option in service["security_opt"]:
            args.extend(["--security-opt", option])
        for key, value in service["environment"].items():
            args.extend(["--env", f"{key}={value}"])
        docker(*args, image)
        docker("start", name)
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            state = json.loads(docker("inspect", name))[0]["State"]
            checks = state["Health"]["Log"]
            if state["Health"]["Status"] == "healthy" and len(checks) >= 3 and all(
                entry["ExitCode"] == 0 for entry in checks[-3:]
            ):
                break
            assert state["Running"], "문의 서비스가 기동 중 종료됨"
            assert state["Health"]["Status"] != "unhealthy", "Docker healthcheck 실패"
            time.sleep(2)
        else:
            raise AssertionError("운영 자원 제한에서 연속 3회 readiness 확인 실패")
        response = docker("exec", "-i", name, "python", "-", input="""
import grpc
from gole.support.v1 import support_agent_pb2, support_agent_pb2_grpc
with grpc.insecure_channel('127.0.0.1:50051') as channel:
    result = support_agent_pb2_grpc.SupportAgentStub(channel).Analyze(
        support_agent_pb2.AnalyzeSupportRequest(ticket_id='synthetic-readiness',
            title='결제 문의', message='결제 확인이 필요합니다.', locale='ko-KR'), timeout=2)
assert result.engine_version == 'rules-v1'
assert result.human_review_required and not result.external_model_used
print('실제 문의 RPC 응답 확인함')
""")
        print(response.strip())
        print(f"운영 CPU {service['cpus']}·메모리 {service['mem_limit']}에서 연속 healthcheck 3회 통과함")
    except BaseException:
        subprocess.run(["docker", "logs", "--tail", "20", name], check=False, timeout=10)
        subprocess.run(["docker", "inspect", "--format", "{{json .State}}", name], check=False, timeout=10)
        raise
    finally:
        subprocess.run(["docker", "rm", "-f", name], check=False, capture_output=True, timeout=30)


if __name__ == "__main__":
    verify(sys.argv[1])
