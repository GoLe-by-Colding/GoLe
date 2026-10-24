"""홍보 시작 전 검증은 실제 워크플로 셸을 실행하고 Docker만 대체한다."""

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import textwrap

import pytest


ROOT = Path(__file__).resolve().parents[4]
WORKFLOW = (ROOT / ".github/workflows/promotion-agent.yml").read_text(encoding="utf-8")


def test_cd_filters_ci_branch_and_preserves_deployment_gate():
    cd = (ROOT / ".github/workflows/cd.yml").read_text(encoding="utf-8")
    trigger = cd.split("  workflow_run:\n", 1)[1].split("  workflow_dispatch:", 1)[0]
    assert "workflows: [CI]" in trigger
    assert "branches: [main]" in trigger
    for guard in (
        "vars.GOLE_PRODUCTION_HOST_READY == 'true'",
        "github.event.workflow_run.event == 'push'",
        "github.event.workflow_run.conclusion == 'success'",
        "github.ref == 'refs/heads/main'",
    ):
        assert guard in cd


def test_image_access_precedes_expensive_setup_and_preserves_capture_reuse():
    assert WORKFLOW.index("Reuse last captures") < WORKFLOW.index("Check capture image access")
    assert WORKFLOW.index("Check capture image access") < WORKFLOW.index("Setup pnpm")
    step = WORKFLOW.split("      - name: Check capture image access\n", 1)[1].split(
        "      - name:", 1
    )[0]
    assert "if: steps.reuse.outputs.reused != 'true'" in step
    assert "packages: read" in WORKFLOW
    assert "--password-stdin" in step
    assert WORKFLOW.count("docker login ghcr.io") == 1
    assert "github.event.workflow_run.conclusion == 'success'" in WORKFLOW


@pytest.mark.parametrize(
    ("failure", "code", "pulls"),
    [
        ("login", "PROMOTION_REGISTRY_LOGIN_FAILED", 0),
        ("minio", "PROMOTION_IMAGE_PULL_FAILED", 1),
        ("mc", "PROMOTION_IMAGE_PULL_FAILED", 2),
        ("", "", 2),
    ],
)
def test_image_access_script_reports_failure_and_stops(tmp_path, failure, code, pulls):
    bash = "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else shutil.which("bash")
    assert bash and Path(bash).is_file(), "워크플로 검증에는 bash가 필요하다"
    match = re.search(
        r"      - name: Check capture image access\n.*?        run: \|\n"
        r"((?:          [^\n]*\n|\n)+)",
        WORKFLOW,
        re.DOTALL,
    )
    assert match
    script = textwrap.dedent(match.group(1))
    mock = """
docker() {
  image="$2"
  case "$image" in
    */minio:*) image=minio ;;
    */mc:*) image=mc ;;
  esac
  printf '%s %s\\n' "$1" "$image" >> "$CALLS"
  if [ "$1" = login ]; then
    cat >/dev/null
    [ "$FAILURE" != login ]
  else
    [ "$FAILURE" != "$image" ]
  fi
}
"""
    summary = tmp_path / "summary"
    calls = tmp_path / "calls"
    event = tmp_path / "event.json"
    event.write_text(json.dumps({"inputs": {"sha": "a" * 40}}))
    result = subprocess.run(
        [bash, "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", mock + script],
        env={
            **os.environ,
            "GHCR_TOKEN": "private-test-token-sentinel",
            "GITHUB_ACTOR": "test-user",
            "GITHUB_WORKSPACE": ROOT.as_posix(),
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_EVENT_PATH": event.as_posix(),
            "RUNNER_TEMP": tmp_path.as_posix(),
            "FAILURE": failure,
            "CALLS": calls.as_posix(),
            "GITHUB_STEP_SUMMARY": summary.as_posix(),
        },
        capture_output=True,
        text=True,
        timeout=10,
    )
    assert result.returncode == (1 if failure else 0), result.stderr
    call_list = calls.read_text().splitlines()
    assert call_list == ["login ghcr.io", "pull minio", "pull mc"][: pulls + 1]
    summary_text = summary.read_text(encoding="utf-8") if summary.exists() else ""
    if failure:
        assert f"::error::{code}" in result.stdout
        assert code in summary_text
    else:
        assert "::error::" not in result.stdout
    assert "private-test-token-sentinel" not in result.stdout + result.stderr + summary_text


@pytest.mark.parametrize(
    ("event_name", "event", "service", "sha", "reuse"),
    [
        ("workflow_run", {"workflow_run": {"head_sha": "a" * 40}}, False, "a" * 40, False),
        ("workflow_dispatch", {"inputs": {"sha": "b" * 40}}, False, "b" * 40, False),
        ("schedule", {}, True, "c" * 40, False),
        ("workflow_dispatch", {"inputs": {"service": "true"}}, True, "c" * 40, True),
        ("workflow_dispatch", {"inputs": {"service": True}}, True, "c" * 40, False),
    ],
)
def test_runtime_environment_reads_event_and_capture_marker(
    tmp_path, event_name, event, service, sha, reuse
):
    bash = "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else shutil.which("bash")
    event_path = tmp_path / "event.json"
    event_path.write_text(json.dumps(event))
    if reuse:
        (tmp_path / "promotion").mkdir()
        (tmp_path / "promotion/reuse").touch()
    result = subprocess.run(
        [
            bash, "--noprofile", "--norc", "-e", "-o", "pipefail", "-c",
            'source "$GITHUB_WORKSPACE/scripts/promotion-workflow-env.sh"; '
            'printf "%s\\n" "$SERVICE" "$SHA" "$REUSE" "$TARGET"; '
            'env | grep -E "^(SHA|SERVICE|REUSE|TARGET|GOLE_MINIO_IMAGE|GOLE_MC_IMAGE)=" || true',
        ],
        env={
            **os.environ,
            "GITHUB_EVENT_NAME": event_name,
            "GITHUB_EVENT_PATH": event_path.as_posix(),
            "GITHUB_SHA": "c" * 40,
            "GITHUB_WORKSPACE": ROOT.as_posix(),
            "RUNNER_TEMP": tmp_path.as_posix(),
        },
        capture_output=True,
        text=True,
        timeout=10,
    )
    assert result.returncode == 0, result.stderr
    assert result.stdout.splitlines() == [
        str(service).lower(), sha, str(reuse).lower(),
        ROOT.as_posix() if reuse else (tmp_path / "target").as_posix(),
    ]


def test_workflow_keeps_promotion_configuration_out_of_runner_environment():
    job_header = WORKFLOW.split("    steps:\n", 1)[0]
    assert "    env:\n" not in job_header
    assert "GITHUB_ENV" not in WORKFLOW
    for name in ("Start API", "Seed E2E accounts and sessions", "Start web"):
        step = WORKFLOW.split(f"      - name: {name}\n", 1)[1].split("      - name:", 1)[0]
        assert "        env:\n" not in step
    agent = WORKFLOW.split("      - name: Run agent\n", 1)[1].split("        run:", 1)[0]
    assert "PROMOTION_AGENT_CAPTURE_ADMIN_PASSWORD:" not in agent
    assert "PROMOTION_AGENT_CODE_SHA:" not in agent
    assert "PROMOTION_GATEWAY_PORT: ${{ vars.PROMOTION_GATEWAY_PORT }}" in agent
    assert "PROMOTION_AGENT_ADMIN_PASSWORD: ${{ secrets.PROMOTION_AGENT_ADMIN_PASSWORD }}" in agent


@pytest.mark.parametrize(
    ("event", "event_name", "exit_code", "service"),
    [
        ({"workflow_run": {"head_sha": "a" * 40}}, "workflow_run", 0, "false"),
        ({"inputs": {"sha": "b" * 40}}, "workflow_dispatch", 0, "false"),
        ({"inputs": {"service": "true"}}, "workflow_dispatch", 0, "true"),
        ({}, "schedule", 0, "true"),
        ({"inputs": {"sha": "$(touch injected)"}}, "workflow_dispatch", 1, "false"),
    ],
)
def test_validate_input_reads_json_without_shell_interpolation(
    tmp_path, event, event_name, exit_code, service
):
    bash = "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else shutil.which("bash")
    match = re.search(
        r"      - name: Validate input\n.*?        run: \|\n"
        r"((?:          [^\n]*\n|\n)+)",
        WORKFLOW, re.DOTALL,
    )
    assert match
    event_path = tmp_path / "event.json"
    event_path.write_text(json.dumps(event))
    output = tmp_path / "output"
    result = subprocess.run(
        [bash, "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", textwrap.dedent(match[1])],
        cwd=tmp_path,
        env={
            **os.environ,
            "GITHUB_EVENT_NAME": event_name,
            "GITHUB_EVENT_PATH": event_path.as_posix(),
            "GITHUB_OUTPUT": output.as_posix(),
        },
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=10,
    )
    assert result.returncode == exit_code, result.stderr
    assert output.read_text().strip() == f"service={service}"
    assert not (tmp_path / "injected").exists()
