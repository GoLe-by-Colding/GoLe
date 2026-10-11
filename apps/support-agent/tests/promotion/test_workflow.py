"""홍보 시작 전 검증은 실제 워크플로 셸을 실행하고 Docker만 대체한다."""

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
    assert "if: env.REUSE != 'true'" in step
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
  printf '%s %s\\n' "$1" "$2" >> "$CALLS"
  if [ "$1" = login ]; then
    cat >/dev/null
    [ "$FAILURE" != login ]
  else
    [ "$FAILURE" != "$2" ]
  fi
}
"""
    summary = tmp_path / "summary"
    calls = tmp_path / "calls"
    result = subprocess.run(
        [bash, "--noprofile", "--norc", "-e", "-o", "pipefail", "-c", mock + script],
        env={
            **os.environ,
            "GHCR_TOKEN": "private-test-token-sentinel",
            "GHCR_USER": "test-user",
            "GOLE_MINIO_IMAGE": "minio",
            "GOLE_MC_IMAGE": "mc",
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
