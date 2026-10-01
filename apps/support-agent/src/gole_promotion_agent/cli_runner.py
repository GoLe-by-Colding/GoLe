"""Claude Code 로 초안을 만드는 실행 경로. 에이전트는 만들기만 하고, 검증과 제출은 여기서 한다.

    claude -p  ──(capture_cli · compose_cli 만 실행)──>  out/captures · out/cards
               ──(구조화 출력)──>  DraftOutput  ──검증──>  BackendPublisher (업로드·생성·검토 요청)

`claude` 프로세스에는 운영 관리자 자격증명을 넘기지 않는다. 허용 목록에 있는 환경변수만 넘기고,
읽기는 `out/` 아래로, 셸은 두 명령으로 묶는다. diff·화면 텍스트에 섞인 지시가 모델을 움직여도
할 수 있는 일은 "찍고, 합성하고, 결과를 쓰는 것"뿐이고 제출 여부는 이 모듈이 정한다.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping, Sequence

from pydantic import ValidationError

from gole_promotion_agent import cli_prompt, policy

DEFAULT_TIMEOUT_SECONDS = 20 * 60
SRC_DIR = Path(__file__).resolve().parent.parent

# claude 프로세스로 넘기는 환경변수. 여기에 없는 것(운영 관리자 계정·GitHub 토큰)은 넘어가지 않는다.
# ANTHROPIC_API_KEY 는 있으면 넘긴다 — 구독 로그인 대신 API 키로 돌릴 때 env 하나로 바꾸기 위해서다.
PASSTHROUGH_ENV = (
    "HOME",
    "USER",
    "LANG",
    "LC_ALL",
    "TMPDIR",
    "XDG_CONFIG_HOME",
    "XDG_CACHE_HOME",
    "CLAUDE_CONFIG_DIR",
    "ANTHROPIC_API_KEY",
    "PLAYWRIGHT_BROWSERS_PATH",
    # Windows 로컬 실행용
    "SYSTEMROOT",
    "USERPROFILE",
    "APPDATA",
    "LOCALAPPDATA",
    "COMSPEC",
    "PATHEXT",
    "TEMP",
    "TMP",
)

Runner = Callable[..., subprocess.CompletedProcess]


@dataclass(frozen=True)
class RunResult:
    outcome: str  # submitted · skipped · failed
    reason: str
    post_id: str | None = None
    session_id: str | None = None


def agent_env(base: Mapping[str, str], *, repo: Path, site: str, demo: bool, session_file: Path) -> dict[str, str]:
    env = {key: base[key] for key in PASSTHROUGH_ENV if base.get(key)}
    # 허용 명령은 `python -m ...` 이다 — 이 인터프리터가 PATH 맨 앞에 와야 같은 의존성으로 돈다.
    env["PATH"] = os.pathsep.join([str(Path(sys.executable).parent), base.get("PATH", "")])
    env["PYTHONPATH"] = str(SRC_DIR)
    env["PROMOTION_AGENT_REPO"] = str(repo)
    env["PROMOTION_AGENT_CAPTURE_SITE"] = site
    env["PROMOTION_AGENT_DATA_SOURCE"] = "DEMO" if demo else "PRODUCTION"
    env["PROMOTION_AGENT_CAPTURE_SESSION_FILE"] = str(session_file)
    return env


def claude_command(claude: str, system_file: Path, model: str | None) -> list[str]:
    command = [
        claude,
        "-p",
        "--output-format",
        "json",
        "--json-schema",
        json.dumps(cli_prompt.OUTPUT_SCHEMA, ensure_ascii=False),
        "--append-system-prompt-file",
        str(system_file),
        "--tools",
        "Bash,Read",
        "--allowedTools",
        f"Bash({cli_prompt.CAPTURE_COMMAND}:*)",
        f"Bash({cli_prompt.COMPOSE_COMMAND}:*)",
        "Read(./out/**)",
        # 허용 목록 밖은 묻지 않고 거부한다 — 무인 실행이라 답할 사람이 없다.
        "--permission-mode",
        "dontAsk",
        "--strict-mcp-config",
    ]
    if model:
        command += ["--model", model]
    return command


class ClaudeCodeDrafter:
    def __init__(
        self,
        *,
        repo: Path,
        run_dir: Path,
        publisher: Any,
        capture_site: str,
        capture_session: Mapping[str, Any] | None,
        demo: bool,
        service: bool,
        sha: str | None = None,
        model: str | None = None,
        claude: str = "claude",
        timeout: float = DEFAULT_TIMEOUT_SECONDS,
        runner: Runner = subprocess.run,
        base_env: Mapping[str, str] | None = None,
    ):
        if not service and not (sha and policy.SHA_PATTERN.match(sha)):
            raise ValueError("INVALID_SHA")
        self._repo = Path(repo)
        self._run_dir = Path(run_dir)
        self._publisher = publisher
        self._site = capture_site.rstrip("/")
        self._session = capture_session
        self._demo = demo
        self._service = service
        self._sha = sha
        self._model = model
        # Windows 에서는 claude 가 .cmd/.exe 라 이름만으로는 CreateProcess 가 찾지 못한다.
        self._claude = shutil.which(claude) or claude
        self._timeout = timeout
        self._runner = runner
        self._base_env = dict(os.environ if base_env is None else base_env)

    @property
    def _out(self) -> Path:
        return self._run_dir / "out"

    def run(self) -> RunResult:
        if self._publisher.pending_count() >= policy.MAX_PENDING_REVIEW:
            return RunResult("skipped", "검토 대기 초안이 가득 참")

        subject, diff = None, None
        if not self._service:
            from gole_promotion_agent.hands import SingleReleaseScanner

            scanner = SingleReleaseScanner(self._repo, self._sha)
            candidates = scanner.candidates()
            if not candidates:
                return RunResult("skipped", "웹 화면 변경이 없는 릴리스")
            if self._publisher.exists(self._sha):
                return RunResult("skipped", "이미 초안이 있는 릴리스")
            subject, diff = candidates[0].subject, scanner.diff(self._sha)

        private = self._run_dir / "private"
        private.mkdir(parents=True, exist_ok=True)
        (self._out / "captures").mkdir(parents=True, exist_ok=True)
        session_file = private / "capture-session.json"
        session_file.write_text(json.dumps(dict(self._session or {})), encoding="utf-8")
        system_file = private / "system.md"
        history = self._publisher.history(policy.HISTORY_LIMIT)
        system_file.write_text(
            cli_prompt.system_prompt(history, self._demo, self._service), encoding="utf-8"
        )

        try:
            completed = self._runner(
                claude_command(self._claude, system_file, self._model),
                cwd=str(self._run_dir),
                env=agent_env(
                    self._base_env,
                    repo=self._repo,
                    site=self._site,
                    demo=self._demo,
                    session_file=session_file,
                ),
                input=cli_prompt.task_prompt(service=self._service, subject=subject, diff=diff),
                capture_output=True,
                text=True,
                encoding="utf-8",
                timeout=self._timeout,
            )
        except subprocess.TimeoutExpired:
            return RunResult("failed", f"claude 시간 초과({int(self._timeout)}초)")
        finally:
            session_file.unlink(missing_ok=True)

        (self._run_dir / "claude-result.json").write_text(completed.stdout or "", encoding="utf-8")
        (self._run_dir / "claude-stderr.log").write_text(completed.stderr or "", encoding="utf-8")
        try:
            result = json.loads(completed.stdout)
        except (TypeError, ValueError):
            return RunResult("failed", f"claude 출력 해석 실패(종료 코드 {completed.returncode})")
        session_id = result.get("session_id")
        if result.get("is_error") or result.get("subtype") != "success":
            return RunResult("failed", f"claude 실패: {result.get('subtype')}", session_id=session_id)
        try:
            draft = cli_prompt.DraftOutput.model_validate(result.get("structured_output") or {})
        except ValidationError as error:
            return RunResult("failed", f"출력 계약 위반 {error.error_count()}건", session_id=session_id)

        if draft.decision == "skip":
            return RunResult("skipped", draft.skip_reason or "", session_id=session_id)

        try:
            paths, captures = self._resolve_images(draft)
        except ValueError as error:
            return RunResult("failed", str(error), session_id=session_id)

        post_id = self._publisher.create(
            None if self._service else self._sha,
            draft.caption,
            self._publisher.upload(paths),
            {
                "captures": captures,
                "rationale": draft.rationale,
                "releaseTitle": subject or "서비스 소개",
            },
        )
        self._publisher.finalize(post_id)
        return RunResult("submitted", "검토 요청함", post_id=post_id, session_id=session_id)

    def _resolve_images(self, draft: cli_prompt.DraftOutput) -> tuple[list[Path], list[dict[str, Any]]]:
        """모델이 고른 이미지 이름을 실제로 찍고 만든 파일과 그 촬영 기록으로 바꾼다."""
        captures = {
            meta["label"]: meta
            for meta in (
                json.loads(path.read_text(encoding="utf-8"))
                for path in sorted((self._out / "captures").glob("*.json"))
            )
        }
        cards_dir = self._out / "cards"
        paths: list[Path] = []
        details: list[dict[str, Any]] = []
        for image in draft.images:
            if image.card:
                meta_file = cards_dir / image.card.replace(".png", ".json")
                if not meta_file.exists():
                    raise ValueError(f"만든 적 없는 카드: {image.card}")
                card = json.loads(meta_file.read_text(encoding="utf-8"))
                source = captures.get(card["capture"])
                path, label = cards_dir / image.card, f"{card['capture']} · 카드"
            else:
                source = captures.get(image.capture)
                path, label = (self._out / "captures" / source["file"]) if source else None, image.capture
            if source is None or path is None or not path.exists():
                raise ValueError(f"찍은 적 없는 화면: {image.card or image.capture}")
            paths.append(path)
            details.append(
                {
                    "label": label,
                    "route": source["route"],
                    "actions": policy.describe_interactions(source.get("interactions", [])),
                    "capturedAt": source.get("captured_at"),
                }
            )
        return paths, details


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gole_promotion_agent.cli_runner")
    parser.add_argument("--repo", required=True, help="찍을 대상 소스(릴리스 worktree)")
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--sha")
    parser.add_argument("--service", action="store_true")
    parser.add_argument("--model", default=os.environ.get("PROMOTION_AGENT_MODEL") or None)
    arguments = parser.parse_args(argv)

    from gole_agent_runtime.privacy import reject_external_tracing
    from gole_promotion_agent.__main__ import DraftLog, _required
    from gole_promotion_agent.hands import BackendPublisher

    reject_external_tracing()
    site = _required("PROMOTION_AGENT_CAPTURE_SITE").rstrip("/")
    capture_api = _required("PROMOTION_AGENT_CAPTURE_API_URL").rstrip("/")
    data_source = os.environ.get("PROMOTION_AGENT_DATA_SOURCE") or "DEMO"
    publisher = BackendPublisher(
        _required("PROMOTION_AGENT_API_URL").rstrip("/"),
        _required("PROMOTION_AGENT_ADMIN_EMAIL"),
        _required("PROMOTION_AGENT_ADMIN_PASSWORD"),
        run={
            "category": "SERVICE" if arguments.service else "FEATURE",
            "dataSource": data_source,
            "runUrl": os.environ.get("PROMOTION_AGENT_RUN_URL") or None,
        },
    )
    capture_login = BackendPublisher(
        capture_api,
        _required("PROMOTION_AGENT_CAPTURE_ADMIN_EMAIL"),
        _required("PROMOTION_AGENT_CAPTURE_ADMIN_PASSWORD"),
    )
    run_dir = Path(arguments.run_dir)
    result = ClaudeCodeDrafter(
        repo=Path(arguments.repo),
        run_dir=run_dir,
        publisher=DraftLog(publisher, run_dir),
        capture_site=site,
        capture_session=capture_login.browser_session(),
        demo=data_source == "DEMO",
        service=arguments.service,
        sha=arguments.sha,
        model=arguments.model,
        claude=os.environ.get("PROMOTION_AGENT_CLAUDE_BIN") or "claude",
    ).run()

    print(f"[promotion-agent] {result.outcome}: {result.reason}")
    if result.session_id:
        print(f"[promotion-agent] claude 세션 {result.session_id}")
    return 1 if result.outcome == "failed" else 0


if __name__ == "__main__":
    raise SystemExit(main())
