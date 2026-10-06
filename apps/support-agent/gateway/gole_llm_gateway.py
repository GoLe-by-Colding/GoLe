#!/usr/bin/env python3
"""SSH 로 부르는 LLM 게이트웨이. 요청 JSON 을 stdin 으로 받아 claude/codex CLI 를 돌리고 JSON 을 돌려준다.

서버의 CLI 는 사용자 본인 계정으로 로그인돼 있다. 서버에는 이 파일과 run.sh 만 둔다(표준 라이브러리만
쓴다). 설치:

    mkdir -p ~/gole-llm-gateway && cp gole_llm_gateway.py run.sh ~/gole-llm-gateway/
    chmod +x ~/gole-llm-gateway/run.sh
    # ~/.ssh/authorized_keys 에 전용 키를 forced command 로 한 줄 추가한다:
    # command="~/gole-llm-gateway/run.sh",no-port-forwarding,no-pty,no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA... gole-promotion

요청:  {"engine": "claude"|"codex", "prompt": str, "system"?: str, "json_schema"?: obj,
        "model"?: str, "images"?: [{"data": base64}], "want_images"?: bool}
응답:  {"ok": bool, "text": str, "structured": obj|null, "images": [{"name", "data"}], "error"?: str,
        "usage": {engine, model, input_tokens, cached_input_tokens, output_tokens, cost_usd, duration_ms}|null}

CLI 는 요청마다 새 임시 폴더에서 돌고, 끝나면 폴더째 지운다. 읽기는 in/ 첨부, 쓰기는 out/ 뿐이다.

응답의 error 는 짧은 문장만 담는다. 호출 쪽(공개 저장소의 Actions)이 로그에 찍으므로, stderr·경로·
예외 원문처럼 서버 사정이 드러나는 내용은 서버의 로그 파일(LOG_PATH, 0600)에만 남긴다.
"""

from __future__ import annotations

import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

MAX_REQUEST_BYTES = 64 * 1024 * 1024
MAX_PROMPT_CHARS = 200_000
MAX_IMAGES = 12
MAX_IMAGE_BYTES = 8 * 1024 * 1024
TIMEOUT_SECONDS = int(os.environ.get("GOLE_GATEWAY_TIMEOUT", "900"))
LOCK_WAIT_SECONDS = 30 * 60
LOCK_PATH = Path(os.environ.get("GOLE_GATEWAY_LOCK", Path.home() / ".cache" / "gole-llm-gateway.lock"))
LOG_PATH = Path(os.environ.get("GOLE_GATEWAY_LOG", Path.home() / ".cache" / "gole-llm-gateway.log"))
# 모델 이름은 CLI 인자로 들어간다. "-" 로 시작하면 옵션으로 읽힐 수 있어 첫 글자를 영숫자로 묶는다.
MODEL_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
# codex 세션 ID 는 파일 경로 조각이 되므로 UUID 모양만 받는다. --json 이 아니면 stderr 머리에 찍힌다.
UUID_PATTERN = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
SESSION_ID_PATTERN = re.compile(rf"^session id: ({UUID_PATTERN.pattern})$", re.MULTILINE)
CONFIG_MODEL_PATTERN = re.compile(r'model\s*=\s*"([A-Za-z0-9][A-Za-z0-9._:-]{0,63})"')
# 응답에 싣는 모델 이름. claude 는 여러 모델을 쓰면 쉼표로 잇는다.
MODEL_LIST_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:,-]{0,255}")

Runner = Callable[..., subprocess.CompletedProcess]


class BadRequest(ValueError):
    pass


def _log(detail: str) -> None:
    """서버에만 남기는 상세 기록. 실패해도 응답을 막지 않는다."""
    try:
        LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        descriptor = os.open(LOG_PATH, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(descriptor, "a", encoding="utf-8") as handle:
            handle.write(f"{datetime.now(timezone.utc).isoformat()} {detail}\n")
    except OSError:
        pass


def _images(request: dict[str, Any], inbox: Path) -> list[Path]:
    images = request.get("images") or []
    if not isinstance(images, list) or len(images) > MAX_IMAGES:
        raise BadRequest(f"images 는 최대 {MAX_IMAGES}장")
    paths = []
    for index, image in enumerate(images, start=1):
        try:
            data = base64.b64decode(image["data"], validate=True)
        except Exception as error:  # noqa: BLE001 — 어떤 형식 오류든 요청 오류다
            raise BadRequest(f"images[{index}] base64 오류") from error
        if len(data) > MAX_IMAGE_BYTES or not data.startswith(b"\x89PNG"):
            raise BadRequest(f"images[{index}] 는 {MAX_IMAGE_BYTES // 1024 // 1024}MB 이하 PNG 여야 한다")
        # 이름은 서버가 정한다 — 요청이 준 이름으로 경로를 만들지 않는다.
        path = inbox / f"img-{index:02d}.png"
        path.write_bytes(data)
        paths.append(path)
    return paths


def _binary(variable: str, default: str) -> str:
    name = os.environ.get(variable) or default
    # Windows 에서는 CLI 가 .cmd/.exe 라 이름만으로는 CreateProcess 가 찾지 못한다.
    return shutil.which(name) or name


def _attachment_note(paths: list[Path], workdir: Path) -> str:
    if not paths:
        return ""
    listed = "\n".join(f"- {path.relative_to(workdir).as_posix()}" for path in paths)
    return f"\n\n첨부 이미지(순서대로):\n{listed}"


def _claude(request: dict[str, Any], workdir: Path, paths: list[Path], runner: Runner) -> dict[str, Any]:
    if request.get("want_images"):
        raise BadRequest("claude 는 이미지를 만들지 못한다 — codex 를 쓴다")
    command = [
        _binary("GOLE_GATEWAY_CLAUDE_BIN", "claude"),
        "-p",
        "--output-format",
        "json",
        "--no-session-persistence",
        "--strict-mcp-config",
        "--tools",
        "Read",
        "--allowedTools",
        "Read(./in/**)",
        "--permission-mode",
        "dontAsk",
    ]
    if request.get("json_schema"):
        command += ["--json-schema", json.dumps(request["json_schema"], ensure_ascii=False)]
    if request.get("system"):
        # 값이 "-" 로 시작해도 옵션으로 읽히지 않게 파일로 넘긴다.
        system_file = workdir / "system.md"
        system_file.write_text(str(request["system"]), encoding="utf-8")
        command += ["--append-system-prompt-file", str(system_file)]
    if request.get("model"):
        command += ["--model", request["model"]]
    completed = runner(
        command,
        cwd=str(workdir),
        input=str(request["prompt"]) + _attachment_note(paths, workdir),
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=TIMEOUT_SECONDS,
    )
    try:
        result = json.loads(completed.stdout)
    except (TypeError, ValueError):
        _log(f"claude 출력 해석 실패 rc={completed.returncode} stderr={(completed.stderr or '')[-2000:]!r}")
        return {"ok": False, "error": f"claude 출력 해석 실패(종료 코드 {completed.returncode})"}
    tokens = result.get("usage") or {}
    cached = _count(tokens.get("cache_read_input_tokens"))
    usage = _usage(
        "claude",
        ",".join(result.get("modelUsage") or {}) or request.get("model"),
        # claude 의 input_tokens 는 캐시를 뺀 값이다. codex 처럼 캐시를 포함한 전체 입력으로 맞춘다.
        input_tokens=_count(tokens.get("input_tokens")) + cached + _count(tokens.get("cache_creation_input_tokens")),
        cached_input_tokens=cached,
        output_tokens=_count(tokens.get("output_tokens")),
        cost_usd=result.get("total_cost_usd"),
        duration_ms=result.get("duration_ms"),
    )
    if result.get("is_error") or result.get("subtype") != "success":
        return {"ok": False, "error": f"claude 실패: {result.get('subtype')}", "usage": usage}
    return {
        "ok": True,
        "text": result.get("result") or "",
        "structured": result.get("structured_output"),
        "usage": usage,
    }


def _codex(request: dict[str, Any], workdir: Path, paths: list[Path], runner: Runner) -> dict[str, Any]:
    last = workdir / "last-message.txt"
    command = [
        _binary("GOLE_GATEWAY_CODEX_BIN", "codex"),
        "exec",
        "--skip-git-repo-check",
        "--ephemeral",
        "-s",
        "workspace-write",
        "-C",
        str(workdir),
        # stdout 을 JSONL 이벤트로 받는다 — 토큰 사용량과 세션 ID 가 여기에만 나온다.
        "--json",
        "-o",
        str(last),
    ]
    if request.get("json_schema"):
        schema = workdir / "schema.json"
        schema.write_text(json.dumps(request["json_schema"], ensure_ascii=False), encoding="utf-8")
        command += ["--output-schema", str(schema)]
    if request.get("model"):
        command += ["-m", request["model"]]
    prompt = str(request["prompt"])
    if request.get("system"):
        prompt = f"{request['system']}\n\n{prompt}"
    if request.get("want_images"):
        prompt += "\n\n만든 이미지는 out/ 폴더에 PNG 파일로 저장한다."
    # 프롬프트는 인자가 아니라 stdin 으로 준다. 인자로 주면 셸 shim(Windows 의 codex.cmd)이 괄호·따옴표
    # 섞인 문장을 깨뜨려 뒤의 -i 까지 사라진다 — 모델이 "첨부 이미지가 없다"고 답한다.
    for path in paths:
        command += ["-i", str(path)]
    started = time.monotonic()
    completed = runner(
        command,
        cwd=str(workdir),
        input=prompt + _attachment_note(paths, workdir),
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=TIMEOUT_SECONDS,
    )
    events = _codex_events(completed.stdout)
    tokens = next((e.get("usage") or {} for e in reversed(events) if e.get("type") == "turn.completed"), {})
    # 실패한 호출도 토큰을 쓴다. 아래 어느 응답이든 사용량을 싣는다.
    usage = _usage(
        "codex",
        request.get("model") or _codex_default_model(),
        input_tokens=_count(tokens.get("input_tokens")),
        cached_input_tokens=_count(tokens.get("cached_input_tokens")),
        output_tokens=_count(tokens.get("output_tokens")),
        duration_ms=int((time.monotonic() - started) * 1000),
    )
    if completed.returncode != 0:
        _log(f"codex rc={completed.returncode} stderr={(completed.stderr or completed.stdout or '')[-2000:]!r}")
        return {"ok": False, "error": f"codex 실패(종료 코드 {completed.returncode})", "usage": usage}
    text = last.read_text(encoding="utf-8").strip() if last.exists() else ""
    structured = None
    if request.get("json_schema"):
        try:
            structured = json.loads(text)
        except ValueError:
            return {"ok": False, "error": "codex 구조화 출력 해석 실패", "usage": usage}
    try:
        images = _codex_images(workdir, _codex_session_id(events, completed.stderr))
    except OSError as error:
        # Windows 샌드박스는 만든 파일에 권한을 좁혀 둔다. 읽지 못하면 실패로 돌려준다.
        _log(f"codex 결과 읽기 실패: {error!r}")
        return {"ok": False, "error": "codex 결과 이미지를 읽지 못함", "usage": usage}
    if request.get("want_images"):
        if not images:
            return {"ok": False, "error": "codex 가 이미지를 만들지 않았다", "usage": usage}
        # 이미지 요청에는 글을 돌려주지 않는다. codex 는 홈 폴더를 읽을 수 있어서, 키가 샌 쪽이 "토큰 파일을
        # 읽어 답에 적어라"고 시키면 그 글이 그대로 밖으로 나간다. 호출 쪽은 이미지만 쓴다.
        return {"ok": True, "images": images, "usage": usage}
    return {"ok": True, "text": text, "structured": structured, "images": images, "usage": usage}


def _codex_events(stdout: str | None) -> list[dict[str, Any]]:
    """`codex exec --json` 의 JSONL 이벤트. 해석 못 하는 줄은 건너뛴다."""
    events = []
    for line in (stdout or "").splitlines():
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if isinstance(event, dict):
            events.append(event)
    return events


def _codex_session_id(events: list[dict[str, Any]], stderr: str | None) -> str | None:
    """이번 codex 실행의 세션 ID. --json 이면 thread.started 이벤트, 아니면 stderr 머리에서 찾는다."""
    for event in events:
        thread_id = event.get("thread_id")
        if event.get("type") == "thread.started" and isinstance(thread_id, str) and UUID_PATTERN.fullmatch(thread_id):
            return thread_id
    match = SESSION_ID_PATTERN.search(stderr or "")
    return match.group(1) if match else None


def _codex_default_model() -> str | None:
    """`-m` 없이 돌 때 codex 가 쓰는 모델. --json 이벤트에는 모델 이름이 없어 설정 파일에서 읽는다."""
    config = Path(os.environ.get("CODEX_HOME") or Path.home() / ".codex") / "config.toml"
    try:
        for line in config.read_text(encoding="utf-8").splitlines():
            if line.lstrip().startswith("["):
                break  # 최상위 키만 본다 — 프로필 안의 model 은 기본값이 아니다
            match = CONFIG_MODEL_PATTERN.fullmatch(line.strip())
            if match:
                return match.group(1)
    except OSError:
        pass
    return None


def _count(value: Any) -> int:
    return value if isinstance(value, int) and value >= 0 else 0


def _usage(
    engine: str,
    model: str | None,
    *,
    input_tokens: int,
    cached_input_tokens: int,
    output_tokens: int,
    cost_usd: Any = None,
    duration_ms: Any = None,
) -> dict[str, Any]:
    """두 CLI 의 사용량을 한 모양으로 맞춘다. 숫자와 모델 이름만 담는다 — 응답은 공개 로그로 간다.

    input_tokens 는 캐시 적중분을 포함한 전체 입력, cached_input_tokens 는 그중 캐시에서 읽은 양이다.
    cost_usd 는 claude 가 알려 주는 API 환산 금액이다(구독이라 실제 청구액이 아니다). codex 는 None.
    """
    return {
        "engine": engine,
        "model": model if isinstance(model, str) and MODEL_LIST_PATTERN.fullmatch(model) else None,
        "input_tokens": input_tokens,
        "cached_input_tokens": cached_input_tokens,
        "output_tokens": output_tokens,
        "cost_usd": float(cost_usd) if isinstance(cost_usd, (int, float)) and cost_usd >= 0 else None,
        "duration_ms": duration_ms if isinstance(duration_ms, int) and duration_ms >= 0 else None,
    }


def _codex_images(workdir: Path, session_id: str | None) -> list[dict[str, str]]:
    """codex 가 만든 PNG. out/ 이 비면 이미지 생성 도구의 세션 폴더에서 가져오고, 그 폴더는 지운다."""
    generated = _generated_folder(session_id) if session_id else None
    try:
        paths = sorted((workdir / "out").glob("*.png"))
        if not paths and generated is not None:
            # 샌드박스가 셸을 못 띄우는 서버(비특권 user namespace 금지)에서는 codex 가 만든 이미지를 out/ 로
            # 옮기지 못한다. 이미지 생성 도구가 저장한 원래 자리에서 가져온다.
            paths = sorted(generated.glob("*.png"))
            if paths:
                _log(f"codex out/ 비어 있어 generated_images 에서 {len(paths)}장 가져옴")
        return [{"name": path.name, "data": base64.b64encode(path.read_bytes()).decode("ascii")} for path in paths]
    finally:
        # 요청 하나가 서버에 남기는 것이 없어야 한다 — 이번 세션이 만든 이미지 폴더도 지운다.
        if generated is not None:
            shutil.rmtree(generated, ignore_errors=True)


def _generated_folder(session_id: str) -> Path:
    """codex 이미지 생성 도구가 세션 결과를 두는 폴더."""
    home = Path(os.environ.get("CODEX_HOME") or Path.home() / ".codex")
    return home / "generated_images" / session_id


def _workdir(root: Path | None) -> Path:
    if os.name != "nt":
        # 0700 — 공용 서버에서 다른 유저가 요청 이미지를 읽지 못하게 한다.
        return Path(tempfile.mkdtemp(prefix="gole-gw-", dir=root))
    # Windows 의 mkdtemp(Python 3.13+)는 소유자 전용 ACL 을 건다. codex 샌드박스 유저가 그 안에 만든
    # 결과 이미지를 이 프로세스가 다시 읽지 못하므로 기본 상속 ACL 로 만든다(로컬 시험 경로 전용).
    path = Path(root or tempfile.gettempdir()) / f"gole-gw-{os.getpid()}-{time.time_ns()}"
    path.mkdir()
    return path


def handle(request: Any, *, runner: Runner = subprocess.run, root: Path | None = None) -> dict[str, Any]:
    if not isinstance(request, dict):
        raise BadRequest("요청은 JSON 객체다")
    engine = request.get("engine")
    if engine not in ("claude", "codex"):
        raise BadRequest("engine 은 claude 또는 codex")
    prompt = request.get("prompt")
    if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > MAX_PROMPT_CHARS:
        raise BadRequest(f"prompt 는 1~{MAX_PROMPT_CHARS}자 문자열")
    model = request.get("model")
    if model is not None and not (isinstance(model, str) and MODEL_PATTERN.fullmatch(model)):
        raise BadRequest("model 은 영문·숫자·._:- 로 된 이름이어야 한다")
    if request.get("system") is not None and not isinstance(request["system"], str):
        raise BadRequest("system 은 문자열이다")
    if request.get("json_schema") is not None and not isinstance(request["json_schema"], dict):
        raise BadRequest("json_schema 는 JSON 객체다")
    workdir = _workdir(root)
    try:
        (workdir / "in").mkdir()
        (workdir / "out").mkdir()
        paths = _images(request, workdir / "in")
        call = _claude if engine == "claude" else _codex
        try:
            response = call(request, workdir, paths, runner)
        except subprocess.TimeoutExpired:
            return {"ok": False, "error": f"{engine} 시간 초과({TIMEOUT_SECONDS}초)"}
        except OSError as error:  # CLI 를 찾지 못함 등 — 경로가 드러나므로 서버 로그에만
            _log(f"{engine} 실행 실패: {error!r}")
            return {"ok": False, "error": f"{engine} 를 실행하지 못함"}
        response.setdefault("images", [])
        response.setdefault("text", "")
        response.setdefault("structured", None)
        response.setdefault("usage", None)
        return response
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


class _Lock:
    """요청을 하나씩만 돌린다 — 구독 사용량 한도와 서버 자원을 동시 실행이 나눠 먹지 않게."""

    def __enter__(self) -> "_Lock":
        try:
            import fcntl
        except ImportError:  # Windows 에서 테스트할 때
            self._handle = None
            return self
        LOCK_PATH.parent.mkdir(parents=True, exist_ok=True)
        self._handle = LOCK_PATH.open("w")
        deadline = time.monotonic() + LOCK_WAIT_SECONDS
        while True:
            try:
                fcntl.flock(self._handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                return self
            except BlockingIOError:
                if time.monotonic() > deadline:
                    raise TimeoutError("다른 요청이 끝나지 않았다")
                time.sleep(2)

    def __exit__(self, *exc: Any) -> None:
        if self._handle is not None:
            self._handle.close()


def _failure(message: str) -> dict[str, Any]:
    return {"ok": False, "error": message, "text": "", "structured": None, "images": [], "usage": None}


def main() -> int:
    raw = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 1)
    try:
        if len(raw) > MAX_REQUEST_BYTES:
            raise BadRequest("요청이 너무 크다")
        request = json.loads(raw.decode("utf-8"))
        with _Lock():
            response = handle(request)
    except BadRequest as error:  # 우리가 쓴 문장이라 그대로 돌려줘도 된다
        response = _failure(str(error))
    except (ValueError, TimeoutError) as error:  # JSON 형식 오류·잠금 대기 초과
        response = _failure("요청을 처리하지 못함" if isinstance(error, ValueError) else str(error))
    except Exception:  # noqa: BLE001 — 클라이언트는 언제나 JSON 을 받아야 한다
        _log("게이트웨이 오류\n" + traceback.format_exc())
        response = _failure("게이트웨이 오류(서버 로그 참고)")
    sys.stdout.write(json.dumps(response, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
