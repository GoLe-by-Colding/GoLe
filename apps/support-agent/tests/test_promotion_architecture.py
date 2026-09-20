"""홍보 모듈의 레이어 경계를 실제로 검사한다.

옛 TypeScript 구현은 `apps/web/scripts/` 아래라 FSD 경계 검사(eslint-plugin-boundaries·
steiger)가 닿지 않는 저장소 유일의 코드였다. 파이썬으로 옮기면서 그 빈칸을 이 파일이 메운다.
"""

import ast
import inspect
from pathlib import Path

from gole_promotion_agent import brain, checkpoints, dryrun, hands, policy, ports, runtime

SDK_PREFIXES = ("anthropic", "playwright", "httpx", "openai", "grpc")


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


def test_ports_declare_contracts_without_sdk_or_environment():
    imported = imported_modules(ports)
    assert not any(name.startswith(SDK_PREFIXES) for name in imported)
    assert "os" not in imported and "subprocess" not in imported


def test_brain_depends_only_on_graph_policy_and_ports():
    assert imported_modules(brain) <= {
        "__future__",
        "pathlib",
        "typing",
        "langgraph.graph",
        "pydantic",
        "gole_promotion_agent",
        "gole_promotion_agent.ports",
        "gole_promotion_agent.session",
    }


def test_hands_never_imports_sdks_at_module_level():
    """기본 설치(`uv sync --locked`)로도 테스트가 전부 돌아야 한다.

    promotion extra 가 없는 CI 환경에서 이 모듈을 import 하는 것만으로 실패하면 안 된다.
    """
    assert not any(name.startswith(SDK_PREFIXES) for name in top_level_imports(hands))
    # 반대로, 지연 import 로라도 실제 SDK 를 쓰고 있어야 한다.
    assert any(name.startswith(SDK_PREFIXES) for name in imported_modules(hands))


def test_dry_run_module_never_touches_sdks_at_all():
    """드라이런은 나가는 경로가 물리적으로 없어야 한다(스펙 D16)."""
    assert not any(name.startswith(SDK_PREFIXES) for name in imported_modules(dryrun))


def test_checkpoints_do_not_depend_on_durable_worker():
    """lease·fencing 을 쓰지 않는다 — 일회성 프로세스에는 분산 조정이 필요 없다."""
    assert not any(
        name.startswith("gole_agent_worker") for name in imported_modules(checkpoints)
    )


def test_runtime_isolates_external_observability():
    """상위 호출자의 tracing context 가 섞이지 않게 실행을 감싼다."""
    assert "gole_agent_runtime.privacy" in imported_modules(runtime)
    # private_execution 은 @wraps 를 쓰므로 감싼 흔적이 __wrapped__ 로 남는다.
    assert hasattr(runtime.PromotionHarness.run, "__wrapped__")


def test_candidate_outcomes_are_structured():
    """후보별 종료가 문자열 자유형이 아니라 정해진 셋이어야 한다."""
    source = inspect.getsource(runtime)
    for outcome in ("submitted", "skipped", "failed"):
        assert f'"{outcome}"' in source


def test_policy_holds_every_limit():
    """상한이 코드 여기저기 흩어지지 않게 한 곳에 모은다."""
    for name in (
        "MAX_WALK_COMMITS",
        "MAX_DRAFTS_PER_RUN",
        "MAX_TURNS",
        "MAX_CONTEXT_IMAGES",
        "MAX_CAPTION",
        "MAX_PENDING_REVIEW",
        "RETENTION_DAYS",
    ):
        assert isinstance(getattr(policy, name), int)
