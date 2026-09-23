"""사진을 영속화하지 않는 내부 LangGraph 실행 경계. quota는 Java가 소유한다."""
from __future__ import annotations

import hmac
import os
from gole_agent_runtime.privacy import reject_external_tracing
from concurrent.futures import ThreadPoolExecutor

import grpc

from gole.brick.v1 import brick_images_pb2 as pb
from gole.brick.v1 import brick_images_pb2_grpc as rpc
from gole_brick_filter.policy import MAX_INPUT, MAX_OUTPUT, PROMPTS
from gole_brick_filter.hands import OpenAIEditor
from gole_brick_filter.runtime import ImageHarness, InactiveRequest, CapacityExceeded, InvalidImage


class BrickImages(rpc.BrickImagesServicer):
    def __init__(self, editor, token: str):
        if len(token) < 32 or any(not 33 <= ord(c) <= 126 for c in token):
            raise ValueError("INTERNAL_TOKEN_REQUIRED")
        self.harness = ImageHarness(editor)
        self.authorization = f"Bearer {token}".encode()

    def Generate(self, request, context):
        values = [v for k, v in context.invocation_metadata() if k == "authorization"]
        if len(values) != 1 or not hmac.compare_digest(values[0].encode(), self.authorization):
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "INTERNAL_AUTH_REQUIRED")
        remaining = context.time_remaining()
        if remaining is None or not 0 < remaining <= 150:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "DEADLINE_REQUIRED")
        if request.mode not in PROMPTS or not 0 < len(request.source_png) <= MAX_INPUT:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "INVALID_IMAGE_REQUEST")

        def check_active():
            if not context.is_active() or context.time_remaining() <= 0:
                raise InactiveRequest()

        try:
            result = self.harness.run(request.mode, request.source_png, check_active)
            check_active()
            return pb.GenerateImageResponse(result_png=result.image)
        except CapacityExceeded:
            context.abort(grpc.StatusCode.RESOURCE_EXHAUSTED, "IMAGE_CAPACITY")
        except InactiveRequest:
            context.abort(grpc.StatusCode.CANCELLED, "IMAGE_CANCELLED")
        except InvalidImage:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "IMAGE_PROCESSING_FAILED")
        except Exception:
            context.abort(grpc.StatusCode.UNAVAILABLE, "IMAGE_PROVIDER_UNAVAILABLE")


def create_server(editor, token: str, port: int = 50053):
    service = BrickImages(editor, token)
    server = grpc.server(
        ThreadPoolExecutor(max_workers=4), maximum_concurrent_rpcs=4,
        options=[("grpc.max_receive_message_length", MAX_INPUT + 1024),
                 ("grpc.max_send_message_length", MAX_OUTPUT + 1024)],
    )
    rpc.add_BrickImagesServicer_to_server(service, server)
    bound = server.add_insecure_port(f"127.0.0.1:{port}")
    if not bound:
        raise RuntimeError("BIND_FAILED")
    return server, bound


def serve():
    if os.environ.get("GOLE_ENVIRONMENT", "local") not in {"local", "development", "dev", "test", "e2e"}:
        raise RuntimeError("PRODUCTION_TRANSPORT_NOT_CONFIGURED")
    if os.environ.get("BRICK_FILTER_PROVIDER_ENABLED") != "true":
        raise RuntimeError("PROVIDER_DISABLED")
    reject_external_tracing()
    from openai import OpenAI
    editor = OpenAIEditor(OpenAI(timeout=120.0, max_retries=0), os.environ.get("BRICK_FILTER_MODEL", "gpt-image-2"))
    server, _ = create_server(editor, os.environ.get("BRICK_FILTER_INTERNAL_TOKEN", ""),
                              int(os.environ.get("BRICK_FILTER_GRPC_PORT", "50053")))
    server.start()
    try:
        server.wait_for_termination()
    finally:
        server.stop(5).wait()


if __name__ == "__main__":
    serve()
