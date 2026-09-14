"""Harness: 실행 한도·취소·안전한 오류·Session을 소유하고 Brain과 Hands를 조립한다."""
from dataclasses import dataclass, field
from threading import BoundedSemaphore
from time import monotonic
from typing import Callable

from gole_brick_filter.brain import build_graph
from gole_brick_filter.hands import sanitize_image
from gole_brick_filter.ports import Editor
from gole_brick_filter.session import Audit, EphemeralSession, Stage
from gole_agent_runtime.privacy import private_execution


class InactiveRequest(Exception):
    pass


class CapacityExceeded(Exception):
    pass


class InvalidImage(Exception):
    pass


class ProviderUnavailable(Exception):
    pass


@dataclass(frozen=True)
class RunResult:
    image: bytes = field(repr=False)
    audit: Audit


class ImageHarness:
    def __init__(self, editor: Editor):
        self._editor = editor
        self._capacity = BoundedSemaphore(2)

    @private_execution
    def run(self, mode: str, source: bytes, check_active: Callable[[], None] = lambda: None) -> RunResult:
        if not self._capacity.acquire(blocking=False):
            raise CapacityExceeded()
        session = EphemeralSession()
        deadline = monotonic() + 150

        def guard():
            check_active()
            if monotonic() >= deadline:
                raise InactiveRequest()

        try:
            graph = build_graph(self._editor, guard, sanitize_image, session.advance)
            result = graph.invoke({"mode": mode, "source": source}, {"callbacks": []})
            guard()
            session.advance(Stage.SUCCEEDED)
            return RunResult(result["result"], session.snapshot())
        except InactiveRequest:
            session.advance(Stage.CANCELLED)
            raise InactiveRequest() from None
        except ValueError:
            session.advance(Stage.FAILED)
            raise InvalidImage() from None
        except Exception:
            session.advance(Stage.FAILED)
            raise ProviderUnavailable() from None
        finally:
            # 취소되더라도 실제 provider 호출이 반환하기 전에는 자리를 돌려주지 않는다.
            self._capacity.release()

    def invoke(self, state):
        """기존 HTTP handler의 graph 인터페이스를 유지하는 전송 어댑터용 연결점."""
        result = self.run(state.get("mode", ""), state.get("source", b""))
        return {"result": result.image, "source": b""}
