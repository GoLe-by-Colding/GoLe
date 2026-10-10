"""로컬 API와 진짜 러너/gateway, 가짜 CLI를 잇는 Playwright용 실행 도우미.

운영 모델과 발행은 호출하지 않는다. requests.json은 로컬 테스트의 비공개 검증 자료다.
"""

from __future__ import annotations

import argparse
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Mapping
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "src"))

from PIL import Image  # noqa: E402

from gole_promotion_agent.drafter import Drafter, UsageMeter, run_payload  # noqa: E402
from gole_promotion_agent.fakes import FakeCamera  # noqa: E402
from gole_promotion_agent.gateway_client import LocalGateway  # noqa: E402
from gole_promotion_agent.hands import BackendPublisher  # noqa: E402

PROPOSAL_CONTENT = "화면 글자를 읽을 수 있는 크기로 유지하고 캡션은 짧게 쓴다."
ROUTES = ("/", "/collection")


def local_api_url(value: str) -> str:
    parsed = urlsplit(value)
    if (parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1", "::1"}
            or parsed.username or parsed.password or parsed.path not in {"", "/"} or parsed.query or parsed.fragment):
        raise argparse.ArgumentTypeError("테스트 API는 http 로컬 원점이어야 한다")
    return value.rstrip("/")


def png() -> bytes:
    out = io.BytesIO()
    Image.new("RGB", (320, 240), "#2f56e6").save(out, format="PNG")
    return out.getvalue()


class Camera(FakeCamera):
    def capture(self, route: str, destination: Path) -> None:
        super().capture(route, destination)
        destination.write_bytes(png())


class FakeCliGateway:
    """요청 직렬화와 CLI 인자 조립까지 통과시키고 subprocess 실행만 대체한다."""

    def __init__(self, phase: str, root: Path):
        self.phase, self.root, self.requests = phase, root, []
        self._module = LocalGateway()._module

    def call(self, request: Mapping[str, Any]) -> dict[str, Any]:
        self.requests.append(dict(request))

        def runner(command, **kwargs):
            if request["engine"] == "claude":
                if "proposals" in request.get("json_schema", {}).get("properties", {}):
                    feedback = json.loads(request["prompt"].split("\n", 1)[1])["feedback"]
                    proposals = [] if self.phase != "propose" else [{
                        "kind": "PROCEDURE", "content": PROPOSAL_CONTENT,
                        "targets": ["CAPTION", "SCREEN_SELECTION", "IMAGE_EDIT"],
                        "categories": ["FEATURE", "SERVICE"], "sourceFeedbackIds": [feedback[0]["id"]],
                    }]
                    structured = {"proposals": proposals}
                else:
                    structured = {
                        "decision": "draft", "caption": "갖고 있는 레고 세트를 한곳에 모아 보세요.",
                        "rationale": "컬렉션 화면의 기능을 소개함", "picks": [{"image": 2, "label": "컬렉션"}],
                    }
                return subprocess.CompletedProcess(command, 0, json.dumps({
                    "subtype": "success", "structured_output": structured, "result": "",
                    "usage": {"input_tokens": 10, "output_tokens": 5},
                }, ensure_ascii=False), "")
            (Path(kwargs["cwd"]) / "out" / "polished.png").write_bytes(png())
            Path(command[command.index("-o") + 1]).write_text("완료", encoding="utf-8")
            events = json.dumps({"type": "turn.completed", "usage": {"input_tokens": 10, "output_tokens": 5}})
            return subprocess.CompletedProcess(command, 0, events, "")

        response = self._module.handle(dict(request), runner=runner, root=self.root)
        if not response.get("ok"):
            raise RuntimeError(response.get("error"))
        return response


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-url", required=True, type=local_api_url)
    parser.add_argument("--run-dir", required=True, type=Path)
    parser.add_argument("--phase", required=True, choices=("propose", "apply"))
    parser.add_argument("--run-key")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.run_key and not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", args.run_key):
        parser.error("run-key는 1~64자의 소문자/숫자/하이픈이다")
    args.run_dir.mkdir(parents=True, exist_ok=True)
    publisher = BackendPublisher(
        args.api_url, os.environ["PROMOTION_AGENT_ADMIN_EMAIL"], os.environ["PROMOTION_AGENT_ADMIN_PASSWORD"],
        run={"category": "SERVICE", "dataSource": "DEMO"},
    )
    gateway = FakeCliGateway(args.phase, args.run_dir)
    meter = UsageMeter(gateway)
    result = Drafter(
        repo=Path.cwd(), run_dir=args.run_dir, publisher=publisher, gateway=meter,
        camera=Camera(ROUTES), routes=ROUTES, demo=True, service=True, run_key=args.run_key,
        claude_model="fake-claude", codex_model="fake-codex",
        log=lambda message: print(message, file=sys.stderr),
    ).run()
    payload = run_payload(result, meter.calls, service=True, sha=None, env={})
    status = publisher.record_run(payload)
    if status not in (200, 201):
        raise RuntimeError(f"실행 원장 기록 실패: HTTP {status}")
    requests_file = args.run_dir / "requests.json"
    requests_file.write_text(json.dumps(gateway.requests, ensure_ascii=False, indent=2), encoding="utf-8")
    context = result.memory_context or {"feedbackIds": [], "guidelines": []}
    output = {
        "postId": result.post_id, "runKey": payload["runKey"], "outcome": result.outcome,
        "reasonCode": result.code, "requestsFile": str(requests_file.resolve()),
        "feedbackIds": context["feedbackIds"], "guidelines": context["guidelines"], "memoryContext": context,
    }
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(output, ensure_ascii=False))
    return 1 if result.outcome == "failed" else 0


if __name__ == "__main__":
    raise SystemExit(main())
