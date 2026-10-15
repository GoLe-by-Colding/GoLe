"""홍보 모듈의 경계를 실제로 검사한다.

두뇌는 게이트웨이 뒤의 codex CLI 이고, 이 패키지들에 남은 것은 손(캡처·백엔드)과 래퍼다. 지켜야 할 경계는
"promotion extra 없이도 import 된다", "모델 SDK 를 직접 부르지 않는다", "공용 core 는 러너 agent 를 모른다"(D29)다.
"""

import ast
from pathlib import Path

from gole_promotion_agent import captures, drafter, drafting, fakes, gateway_client, hands, memory, policy, publisher
from gole_promotion_core import contract, gateway, ledger, limits, order
from gole_promotion_deliver import __main__ as deliver_main, deliver

SDK_PREFIXES = ("anthropic", "playwright", "httpx", "openai", "grpc", "langgraph", "langchain")
AGENT = (captures, drafter, drafting, fakes, gateway_client, hands, memory, policy, publisher)
CORE = (contract, gateway, ledger, limits, order)
DELIVER = (deliver, deliver_main)
MODULES = AGENT + CORE + DELIVER


def imported_modules(module) -> set[str]:
    tree = ast.parse(Path(module.__file__).read_text(encoding="utf-8"))
    return {node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)} | {
        alias.name for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names
    }


def top_level_imports(module) -> set[str]:
    """함수 밖, 모듈 최상단에서 이뤄지는 import만 센다."""
    tree = ast.parse(Path(module.__file__).read_text(encoding="utf-8"))
    names: set[str] = set()
    for node in tree.body:
        if isinstance(node, ast.ImportFrom) and node.module:
            names.add(node.module)
        elif isinstance(node, ast.Import):
            names.update(alias.name for alias in node.names)
    return names


def test_no_module_imports_sdks_at_module_level():
    """기본 설치(`uv sync --locked`)로도 테스트가 전부 돌아야 한다."""
    for module in MODULES:
        assert not any(name.startswith(SDK_PREFIXES) for name in top_level_imports(module)), module


def test_package_never_calls_a_model_sdk_directly():
    """모델 호출은 게이트웨이(CLI)뿐이다. 패키지 안에 모델 SDK 경로가 다시 생기지 않게 한다."""
    for module in MODULES:
        assert not any(
            name.startswith(("anthropic", "openai", "langgraph", "langchain"))
            for name in imported_modules(module)
        ), module


def test_runner_isolates_external_observability():
    """상위 호출자의 tracing 설정이 섞이지 않게 실행 전에 막는다."""
    assert "gole_agent_runtime.privacy" in imported_modules(drafter)


def test_server_side_never_imports_the_runner_package():
    """서버 이미지에는 core·deliver 만 들어간다(D29). agent 를 끌어오면 서버에서 import 가 깨진다."""
    for module in CORE + DELIVER:
        assert not any(name.startswith("gole_promotion_agent") for name in imported_modules(module)), module


def test_limits_live_in_one_place():
    """러너와 서버가 같이 지키는 상한은 core.limits, 러너만 쓰는 상한은 policy 에 모은다."""
    for name in ("MAX_CAPTION", "MAX_RATIONALE", "MAX_PENDING_REVIEW", "MAX_RUN_DETAIL", "MAX_RUN_CALLS"):
        assert isinstance(getattr(limits, name), int)
        assert not hasattr(policy, name), name
    assert isinstance(policy.MAX_DIFF_CHARS, int)
