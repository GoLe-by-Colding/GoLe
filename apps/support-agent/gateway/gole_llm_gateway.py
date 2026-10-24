#!/usr/bin/env python3
"""LLM 게이트웨이. 요청 JSON 하나를 받아 codex CLI 를 돌리고 JSON 을 돌려준다(stdin/stdout 또는 call()).

지인 서버의 일회용 Docker 컨테이너 안에서 돈다(promotion-review D26·D28·D29). 주문 하나를 처리하는
`gole_promotion_deliver` 가 같은 컨테이너에서 call() 로 기획·제작 두 번을 부른다. 코드 이미지는 `gateway/Dockerfile`
(GHCR `:stable`)이고, 지인 서버가 그 위에 codex 로그인을 얹어 `:local` 을 굽는다. 서버 진입점은 `server/run.sh` 다.
서버 설치(한 번. docker(BuildKit), flock·setsid(util-linux), curl 이 있어야 한다):

    mkdir -p /opt/gole-gateway   # 이 저장소의 gateway/server/{run.sh,Dockerfile.local,.dockerignore} 를 복사
    docker run --name gole-login -it --entrypoint codex \\
      ghcr.io/gole-by-colding/gole-llm-gateway:stable login --device-auth
    docker cp gole-login:/home/gateway/.codex/auth.json /opt/gole-gateway/auth.json && docker rm gole-login
    chmod 600 /opt/gole-gateway/auth.json
    docker volume create gole-gateway-state
    # 납품 설정(0600). 토큰은 여기서 만들고, 그 SHA-256 만 운영 백엔드의 PROMOTION_AGENT_TOKEN_SHA256 에 넣는다:
    #   TOKEN=$(openssl rand -hex 32); printf '%s' "$TOKEN" | sha256sum
    # /opt/gole-gateway/promotion.env
    #   PROMOTION_AGENT_API_URL=https://gole.co.kr
    #   PROMOTION_AGENT_TOKEN=<TOKEN>
    #   DISCORD_OPERATIONS_WEBHOOK_URL=<운영 채널 웹훅>
    # ~/.ssh/authorized_keys 에 전용 키를 forced command 로 한 줄 추가한다:
    # restrict,command="/opt/gole-gateway/run.sh" ssh-ed25519 AAAA... gole-promotion
    # restrict 는 포워딩·pty 등 SSH 부가 기능을 앞으로 생길 것까지 끈다.

run.sh 는 `submit <run_key>` 로 주문서를 받아 jobs/ 에 두고 바로 "접수"로 답한 뒤, 백그라운드에서 stable 을 받아
바뀌었으면 다시 굽고 deliver 컨테이너를 돌린다. 끝나면 codex 가 갱신한 auth.json 을 꺼내 바뀌었으면 다시 굽는다.
옛 이미지 정리(prune)도 그때 한다. 컨테이너 제약(--log-driver none 등)은 run.sh 에 있다.
상태 볼륨은 이미지 안 경로(/home/gateway/state)로 처음 붙여야 게이트웨이 사용자 소유가 된다(2026-10-13 실측).
CODEX_HOME 에는 아무것도 붙이지 않는다 — 진입점은 사람이 쓰던 흔적(history.jsonl·AGENTS.md 등)을 보면 지우지 않고
GATEWAY_MISCONFIGURED 로 멈춘다.

요청:  {"engine": "codex", "prompt": str, "system"?: str, "json_schema"?: obj,
        "model"?: str, "reasoning"?: "low"|"medium"|"high", "images"?: [{"data": base64}], "want_images"?: bool}
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
MAX_IMAGES = 24  # 홍보 후보 화면(현재 11장) 전부를 한 호출에 싣는다
MAX_IMAGE_BYTES = 8 * 1024 * 1024
TIMEOUT_SECONDS = int(os.environ.get("GOLE_GATEWAY_TIMEOUT", "900"))
LOCK_WAIT_SECONDS = 30 * 60
LOCK_PATH = Path(os.environ.get("GOLE_GATEWAY_LOCK", Path.home() / ".cache" / "gole-llm-gateway.lock"))
LOG_PATH = Path(os.environ.get("GOLE_GATEWAY_LOG", Path.home() / ".cache" / "gole-llm-gateway.log"))
# 계정 가드(D27). 기록은 잠금 파일 옆(Docker 에서는 상태 볼륨, D28)에 둔다.
USAGE_PATH = Path(os.environ.get("GOLE_GATEWAY_USAGE", LOCK_PATH.parent / "gole-llm-gateway-usage.jsonl"))
DAILY_INPUT_TOKENS = int(os.environ.get("GOLE_GATEWAY_DAILY_INPUT_TOKENS", "2000000"))
_COOLDOWN_HOURS = float(os.environ.get("GOLE_GATEWAY_COOLDOWN_HOURS", "6"))
# 이 코드로 끝난 호출 뒤에는 그만큼 CLI 를 부르지 않는다 — 한도·로그인 문제에 계정을 다시 두드리지 않게.
COOLDOWN_SECONDS = {
    "CODEX_USAGE_LIMIT": _COOLDOWN_HOURS * 3600,
    "CODEX_AUTH_REQUIRED": _COOLDOWN_HOURS * 3600,
    "CODEX_RATE_LIMIT": 30 * 60,
}
KEEP_SECONDS = 7 * 24 * 3600
# 모델 이름은 CLI 인자로 들어간다. "-" 로 시작하면 옵션으로 읽힐 수 있어 첫 글자를 영숫자로 묶는다.
MODEL_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
# codex 세션 ID 는 파일 경로 조각이 되므로 UUID 모양만 받는다. --json 이 아니면 stderr 머리에 찍힌다.
UUID_PATTERN = re.compile(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
SESSION_ID_PATTERN = re.compile(rf"^session id: ({UUID_PATTERN.pattern})$", re.MULTILINE)
CONFIG_MODEL_PATTERN = re.compile(r'model\s*=\s*"([A-Za-z0-9][A-Za-z0-9._:-]{0,63})"')
# 응답에 싣는 모델 이름.
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
    if request.get("reasoning"):
        # 고르기처럼 가벼운 호출은 추론을 낮춰 사용량을 아낀다. 값은 handle 이 목록으로 거른다.
        command += ["-c", f'model_reasoning_effort="{request["reasoning"]}"']
    prompt = str(request["prompt"])
    if request.get("system"):
        prompt = f"{request['system']}\n\n{prompt}"
    if request.get("want_images"):
        prompt = ("$imagegen\n내장 image_gen 도구로 지시한 첨부 이미지를 편집한다. "
                  "도구를 사용할 수 없으면 실패로 종료하고 API/CLI 이미지 생성으로 대체하지 않는다.\n\n" + prompt)
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
    failed = [e for e in events if e.get("type") in ("turn.failed", "error")]
    if completed.returncode != 0 or any(e.get("type") == "turn.failed" for e in events):
        detail = "\n".join([*(json.dumps(e, ensure_ascii=False) for e in failed), completed.stderr or ""])
        _log(f"codex rc={completed.returncode} detail={(detail or completed.stdout or '')[-2000:]!r}")
        # 계정 문제(한도·로그인)와 코드 결함을 공개 로그에서 구별하도록 고정 코드로 돌려준다. 원문은 서버 로그에만.
        code = _classify(detail if detail.strip() else completed.stdout or "")
        return {"ok": False, "error": code or f"codex 실패(종료 코드 {completed.returncode})", "usage": usage}
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
        if not images and structured is None:
            return {"ok": False, "error": "codex 가 이미지를 만들지 않았다", "usage": usage}
        # 이미지 요청에는 자유 글을 돌려주지 않는다. codex 는 홈 폴더를 읽을 수 있어서, 키가 샌 쪽이 "토큰 파일을
        # 읽어 답에 적어라"고 시키면 그 글이 그대로 밖으로 나간다. 구조화 출력은 호출 쪽이 스키마로 검증하고
        # 사람 검토 전에는 운영 DB 에만 둔다(D23·D25). 이미지가 없으면 호출 쪽이 원본을 쓴다.
        return {"ok": True, "structured": structured, "images": images, "usage": usage}
    if structured is not None:
        # 스키마 요청은 구조화 출력만 쓴다. 자유 글은 돌려주지 않는다(D25) — 위 이미지 요청과 같은 이유다.
        return {"ok": True, "structured": structured, "images": images, "usage": usage}
    return {"ok": True, "text": text, "structured": structured, "images": images, "usage": usage}


# codex 실패 원문에서 찾는 표지 → 고정 코드. 앞쪽이 먼저 이긴다. 영문 표지는 CLI 의 CodexErrorInfo 값과 안내 문구다.
_ERROR_MARKERS: tuple[tuple[tuple[str, ...], str], ...] = (
    (("usage_limit_exceeded", "usage limit"), "CODEX_USAGE_LIMIT"),
    (("rate_limit_exceeded", "rate limit", "too many requests"), "CODEX_RATE_LIMIT"),
    (("unauthorized", "not logged in", "not signed in", "could not be refreshed", "refresh token was rejected"),
     "CODEX_AUTH_REQUIRED"),
    (("server_overloaded", "service unavailable"), "CODEX_OVERLOADED"),
    (("context_window_exceeded",), "CODEX_CONTEXT_TOO_LARGE"),
)


def _classify(detail: str) -> str | None:
    lowered = detail.casefold()
    return next((code for markers, code in _ERROR_MARKERS if any(m in lowered for m in markers)), None)


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
    duration_ms: Any = None,
) -> dict[str, Any]:
    """CLI 사용량. 숫자와 모델 이름만 담는다 — 응답은 공개 로그로 간다.

    input_tokens 는 캐시 적중분을 포함한 전체 입력, cached_input_tokens 는 그중 캐시에서 읽은 양이다.
    cost_usd 는 원장 계약(ModelCall)의 자리다. codex 는 금액을 알려 주지 않아 늘 None 이다.
    """
    return {
        "engine": engine,
        "model": model if isinstance(model, str) and MODEL_LIST_PATTERN.fullmatch(model) else None,
        "input_tokens": input_tokens,
        "cached_input_tokens": cached_input_tokens,
        "output_tokens": output_tokens,
        "cost_usd": None,
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
    if engine != "codex":
        raise BadRequest("engine 은 codex 뿐이다")
    prompt = request.get("prompt")
    if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > MAX_PROMPT_CHARS:
        raise BadRequest(f"prompt 는 1~{MAX_PROMPT_CHARS}자 문자열")
    model = request.get("model")
    if model is not None and not (isinstance(model, str) and MODEL_PATTERN.fullmatch(model)):
        raise BadRequest("model 은 영문·숫자·._:- 로 된 이름이어야 한다")
    if request.get("reasoning") is not None and request["reasoning"] not in ("low", "medium", "high"):
        raise BadRequest("reasoning 은 low·medium·high 중 하나")
    if request.get("system") is not None and not isinstance(request["system"], str):
        raise BadRequest("system 은 문자열이다")
    if request.get("json_schema") is not None and not isinstance(request["json_schema"], dict):
        raise BadRequest("json_schema 는 JSON 객체다")
    workdir = _workdir(root)
    try:
        (workdir / "in").mkdir()
        (workdir / "out").mkdir()
        paths = _images(request, workdir / "in")
        try:
            response = _codex(request, workdir, paths, runner)
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
                    raise TimeoutError("GATEWAY_BUSY")
                time.sleep(2)

    def __exit__(self, *exc: Any) -> None:
        if self._handle is not None:
            self._handle.close()


class _AccountGuard:
    """구독 계정을 지킨다(D27). 호출 기록을 볼륨에 남기고, 예산을 넘었거나 계정 오류 직후면 CLI 를 부르지 않는다.

    호출 쪽이 무엇이든(러너 재시도·수동 실행) 같은 계정을 두드리는 곳은 여기 하나라 여기서 막는다.
    _Lock 안에서만 쓰므로 기록 파일에 경합이 없다.
    """

    def __init__(self, path: Path, *, now: Callable[[], float] = time.time):
        self._path = path
        self._now = now

    def _records(self) -> list[dict[str, Any]]:
        try:
            lines = self._path.read_text(encoding="utf-8").splitlines()
        except OSError:
            return []
        records = []
        for line in lines:
            try:
                record = json.loads(line)
            except ValueError:
                continue
            if isinstance(record, dict) and isinstance(record.get("t"), (int, float)):
                records.append(record)
        return records

    def refusal(self) -> str | None:
        """지금 CLI 를 부르면 안 되는 이유(고정 코드). 불러도 되면 None."""
        now, records = self._now(), self._records()
        # 막은 요청은 기록하지 않으므로 마지막 줄이 마지막으로 CLI 를 부른 결과다.
        if records and now - records[-1]["t"] < COOLDOWN_SECONDS.get(records[-1].get("code"), 0):
            return "CODEX_COOLDOWN"
        spent = sum(_count(r.get("input")) for r in records if now - r["t"] < 24 * 3600)
        return "GATEWAY_BUDGET_EXCEEDED" if spent >= DAILY_INPUT_TOKENS else None

    def record(self, response: dict[str, Any]) -> None:
        now = self._now()
        usage = response.get("usage") or {}
        error = response.get("error")
        kept = [r for r in self._records() if now - r["t"] < KEEP_SECONDS]
        kept.append({
            "t": now, "code": None if response.get("ok") else (error if error in _CODES else "FAILED"),
            "input": _count(usage.get("input_tokens")), "output": _count(usage.get("output_tokens")),
        })
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._path.write_text("".join(json.dumps(r) + "\n" for r in kept), encoding="utf-8")


_CODES = {code for _, code in _ERROR_MARKERS}


def _failure(message: str) -> dict[str, Any]:
    return {"ok": False, "error": message, "text": "", "structured": None, "images": [], "usage": None}


def call(request: Any) -> dict[str, Any]:
    """요청 하나를 잠금·계정 가드 안에서 처리한다. 언제나 응답 JSON 을 돌려준다 — 같은 컨테이너의 deliver(D29)도 이걸 부른다."""
    try:
        with _Lock():
            guard = _AccountGuard(USAGE_PATH)
            refusal = guard.refusal()
            if refusal:
                _log(f"계정 가드가 막음: {refusal}")
                return _failure(refusal)
            response = handle(request)
            guard.record(response)
            return response
    except BadRequest as error:  # 우리가 쓴 문장이라 그대로 돌려줘도 된다
        return _failure(str(error))
    except TimeoutError as error:  # 잠금 대기 초과
        return _failure(str(error))
    except ValueError:  # 요청 모양 오류
        return _failure("요청을 처리하지 못함")
    except Exception:  # noqa: BLE001 — 호출 쪽은 언제나 JSON 을 받아야 한다
        _log("게이트웨이 오류\n" + traceback.format_exc())
        return _failure("게이트웨이 오류(서버 로그 참고)")


def main() -> int:
    raw = sys.stdin.buffer.read(MAX_REQUEST_BYTES + 1)
    if len(raw) > MAX_REQUEST_BYTES:
        response = _failure("요청이 너무 크다")
    else:
        try:
            request = json.loads(raw.decode("utf-8"))
        except ValueError:
            request = None
        response = _failure("요청을 처리하지 못함") if request is None else call(request)
    sys.stdout.write(json.dumps(response, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
