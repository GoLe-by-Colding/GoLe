from __future__ import annotations

import os
import threading
from dataclasses import dataclass
from typing import Protocol

from gole_agent_worker.model import LeaseLost, PermanentFailure, TransientFailure


@dataclass(frozen=True)
class ProviderInput:
    topic: str
    operation_key: str
    authorization_ref: str


@dataclass(frozen=True)
class ProviderOutput:
    text: str
    external_model_used: bool


class Provider(Protocol):
    # adapter는 Java 승인 참조를 보존한다. 여기서 quota를 차감/재구현하지 않는다.
    def generate(self, request: ProviderInput, *, timeout: float,
                 cancelled: threading.Event) -> ProviderOutput: ...


class FakeProvider:
    def generate(self, request: ProviderInput, *, timeout: float,
                 cancelled: threading.Event) -> ProviderOutput:
        if cancelled.is_set():
            raise LeaseLost()
        return ProviderOutput("브릭 색상 예시: 빨강, 노랑, 파랑", False)


class OpenAIProvider:
    def __init__(self, client=None):
        if os.environ.get("AGENT_OPENAI_ENABLED") != "true":
            raise ValueError("EXTERNAL_DISABLED")
        if client is None:
            from openai import OpenAI
            key = os.environ.get("OPENAI_API_KEY")
            if not key:
                raise ValueError("OPENAI_KEY_REQUIRED")
            client = OpenAI(api_key=key, max_retries=0)
        self.client = client

    def generate(self, request: ProviderInput, *, timeout: float,
                 cancelled: threading.Event) -> ProviderOutput:
        if cancelled.is_set():
            raise LeaseLost()
        if request.topic != "brick-colors":
            raise PermanentFailure()
        from openai import APIConnectionError, APIStatusError, APITimeoutError
        try:
            response = self.client.responses.create(
                model=os.environ.get("AGENT_OPENAI_MODEL", "gpt-4.1-mini"),
                input="List three common toy brick colors in Korean. No personal data.",
                max_output_tokens=128, store=False, timeout=timeout,
            )
        except (APIConnectionError, APITimeoutError):
            raise TransientFailure() from None
        except APIStatusError as error:
            if error.status_code == 429 or error.status_code >= 500:
                raise TransientFailure() from None
            raise PermanentFailure() from None
        if cancelled.is_set():
            raise LeaseLost()
        if not response.output_text or len(response.output_text) > 4096:
            raise PermanentFailure()
        return ProviderOutput(response.output_text, True)
