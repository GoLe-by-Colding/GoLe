"""이 패키지의 서버들이 같은 loopback 포트를 기본값으로 잡지 않는지 검사한다.

에이전트가 넷인데 전부 같은 호스트의 loopback 에 뜨므로, 기본 포트가 겹치면 나중에 뜨는
쪽이 bind 에 실패한다. 실제로 사진 HTTP 서버가 영속 작업자의 :50052 에 앉아 있었고, 둘 다
배포돼 있지 않아 아무도 모르고 지나갔다. 그래서 켜 보지 않고도 걸리게 여기서 막는다.

포트 상수를 한 곳에 모으지는 않는다 — 세 패키지는 프로세스·이미지가 갈린 별개의 서비스이고,
지금 공유하는 코드는 `gole_agent_runtime.privacy` 뿐이다. 대신 각자 선언한 기본값을 읽어다
비교한다. 새 서버가 생겨도 `*_PORT` 환경변수를 쓰기만 하면 자동으로 검사 대상이 된다.
"""

import ast
import importlib
from pathlib import Path

import pytest

# 서버를 bind 하는 모듈. import 만으로 서버가 뜨지는 않는다(전부 serve() 안에서 연다).
SERVER_MODULES = (
    "gole_support_agent.server",
    "gole_agent_worker.bootstrap",
    "gole_brick_filter.server",
    "gole_brick_filter.grpc_server",
)


def declared_ports(module_name):
    """`os.environ.get("..._PORT", <기본값>)` 의 기본값을 모듈 이름공간에서 평가해 돌려준다.

    기본값이 리터럴("50051")이든 상수 참조(str(DEFAULT_HTTP_PORT))든 똑같이 잡힌다.
    """
    module = importlib.import_module(module_name)
    tree = ast.parse(Path(module.__file__).read_text(encoding="utf-8"))
    found = {}
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call) or len(node.args) != 2:
            continue
        target = node.func
        if not (isinstance(target, ast.Attribute) and target.attr == "get"):
            continue
        key = node.args[0]
        if not (isinstance(key, ast.Constant) and isinstance(key.value, str) and key.value.endswith("_PORT")):
            continue
        # 기본값은 모듈이 실제로 보는 이름공간에서 평가한다.
        value = eval(compile(ast.Expression(node.args[1]), "<default>", "eval"), vars(module))  # noqa: S307
        found[key.value] = int(value)
    return found


def test_every_server_declares_an_environment_overridable_port():
    for module_name in SERVER_MODULES:
        assert declared_ports(module_name), f"{module_name} 이 기본 포트를 환경변수로 열어두지 않았다"


def test_default_ports_do_not_collide():
    claims = {}
    for module_name in SERVER_MODULES:
        for env_key, port in declared_ports(module_name).items():
            claims.setdefault(port, []).append(f"{module_name}:{env_key}")

    collisions = {port: owners for port, owners in claims.items() if len(owners) > 1}
    assert not collisions, f"같은 기본 포트를 여럿이 잡았다: {collisions}"


def test_allocation_matches_the_documented_assignment():
    """스펙(`.kiro/specs/agent-worker/brick-grpc.md`)이 정한 배분과 어긋나지 않게 고정한다.

    ":50052 를 재사용하지 않는다"가 그 문서의 문장이고, 영속 작업자가 그 포트의 주인이다.
    Java 쪽 짝(gole.brick-filter.endpoint / durable.target)이 이 숫자를 기본값으로 들고
    있으므로, 바꾸려면 양쪽을 함께 옮겨야 한다.
    """
    assignment = {
        "gole_support_agent.server": 50051,
        "gole_agent_worker.bootstrap": 50052,
        "gole_brick_filter.grpc_server": 50053,
        "gole_brick_filter.server": 50054,
    }
    for module_name, expected in assignment.items():
        ports = set(declared_ports(module_name).values())
        assert ports == {expected}, f"{module_name} 기본 포트가 {expected} 에서 {ports} 로 바뀌었다"


@pytest.mark.parametrize("module_name", SERVER_MODULES)
def test_ports_stay_on_loopback_by_default(module_name):
    """기본 bind 주소가 0.0.0.0 으로 넓어지지 않았는지 본다.

    support 서버만 컨테이너 안에서 돌려고 0.0.0.0 을 쓰고(compose 가 127.0.0.1 로 매핑한다),
    나머지는 내부 실행 경계라 loopback 을 벗어나면 안 된다.
    """
    module = importlib.import_module(module_name)
    source = Path(module.__file__).read_text(encoding="utf-8")
    if module_name == "gole_support_agent.server":
        pytest.skip("컨테이너 안에서 bind 하고 compose 가 127.0.0.1 로 매핑한다")
    assert "0.0.0.0" not in source
