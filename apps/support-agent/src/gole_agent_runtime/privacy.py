"""문의와 사진 실행에 외부 관측 context가 섞이지 않게 하는 공통 경계."""
from functools import wraps
import os

from langsmith import tracing_context
from langchain_core.runnables.config import set_config_context


TRACING_KEYS = (
    "LANGSMITH_TRACING", "LANGSMITH_TRACING_V2", "LANGCHAIN_TRACING", "LANGCHAIN_TRACING_V2",
)


def reject_external_tracing():
    if any(os.environ.get(key, "").strip().lower() == "true" for key in TRACING_KEYS):
        raise ValueError("EXTERNAL_TRACING_FORBIDDEN")


def private_execution(function):
    @wraps(function)
    def wrapped(*args, **kwargs):
        # 환경 변수 검사만으로는 상위 호출자의 tracing context를 막을 수 없다.
        with tracing_context(enabled=False, parent=False):
            # config의 callbacks=[]만 넘기면 LangGraph가 상위 callback을 합칠 수 있다.
            # 독립 복사 context에서 실행하며 호출자의 원래 context는 변경하지 않는다.
            with set_config_context({"callbacks": [], "tags": [], "metadata": {}, "configurable": {}}) as context:
                return context.run(function, *args, **kwargs)
    return wrapped
