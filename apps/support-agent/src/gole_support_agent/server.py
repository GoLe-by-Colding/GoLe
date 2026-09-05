from __future__ import annotations

import logging
import os
import signal
from concurrent import futures

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc

from gole.support.v1 import support_agent_pb2, support_agent_pb2_grpc
from gole_support_agent.agent import analyze_support

LOGGER = logging.getLogger("gole.support-agent")
MAX_TITLE_LENGTH = 100
MAX_MESSAGE_LENGTH = 2_000
MAX_TICKET_ID_LENGTH = 128

CATEGORY_TO_NAME = {
    support_agent_pb2.SUPPORT_CATEGORY_GENERAL: "GENERAL",
    support_agent_pb2.SUPPORT_CATEGORY_TRADE: "TRADE",
    support_agent_pb2.SUPPORT_CATEGORY_PAYMENT: "PAYMENT",
    support_agent_pb2.SUPPORT_CATEGORY_PRODUCT_FEEDBACK: "PRODUCT_FEEDBACK",
    support_agent_pb2.SUPPORT_CATEGORY_PRIVACY_ACCESS: "PRIVACY_ACCESS",
    support_agent_pb2.SUPPORT_CATEGORY_PRIVACY_CORRECTION_DELETION: "PRIVACY_CORRECTION_DELETION",
    support_agent_pb2.SUPPORT_CATEGORY_PRIVACY_PROCESSING_STOP: "PRIVACY_PROCESSING_STOP",
}
NAME_TO_CATEGORY = {name: value for value, name in CATEGORY_TO_NAME.items()}
NAME_TO_PRIORITY = {
    "LOW": support_agent_pb2.SUPPORT_PRIORITY_LOW,
    "NORMAL": support_agent_pb2.SUPPORT_PRIORITY_NORMAL,
    "HIGH": support_agent_pb2.SUPPORT_PRIORITY_HIGH,
    "URGENT": support_agent_pb2.SUPPORT_PRIORITY_URGENT,
}


class SupportAgentService(support_agent_pb2_grpc.SupportAgentServicer):
    def Analyze(self, request, context):  # noqa: N802 - gRPC가 proto 메서드명을 사용함
        ticket_id = request.ticket_id.strip()
        title = request.title.strip()
        message = request.message.strip()
        if not ticket_id or len(ticket_id) > MAX_TICKET_ID_LENGTH:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "ticket_id가 올바르지 않습니다.")
        if not title or len(title) > MAX_TITLE_LENGTH:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "title이 올바르지 않습니다.")
        if not message or len(message) > MAX_MESSAGE_LENGTH:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "message가 올바르지 않습니다.")
        declared_category = CATEGORY_TO_NAME.get(request.declared_category, "GENERAL")

        result = analyze_support(
            ticket_id=ticket_id,
            declared_category=declared_category,
            title=title,
            message=message,
            locale=request.locale or "ko-KR",
        )
        # 문의 원문·제목·사용자 식별자는 로그에 남기지 않는다.
        LOGGER.info(
            "문의 분석 완료 category=%s priority=%s engine=%s",
            result["recommended_category"],
            result["priority"],
            result["engine_version"],
        )
        return support_agent_pb2.AnalyzeSupportResponse(
            recommended_category=NAME_TO_CATEGORY[result["recommended_category"]],
            priority=NAME_TO_PRIORITY[result["priority"]],
            summary=result["summary"],
            draft_reply=result["draft_reply"],
            risk_flags=result["risk_flags"],
            human_review_required=result["human_review_required"],
            external_model_used=result["external_model_used"],
            engine_version=result["engine_version"],
        )


def serve() -> None:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
    port = int(os.environ.get("SUPPORT_AGENT_GRPC_PORT", "50051"))
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=int(os.environ.get("SUPPORT_AGENT_WORKERS", "4"))),
        options=(
            ("grpc.max_receive_message_length", 16 * 1024),
            ("grpc.max_send_message_length", 16 * 1024),
        ),
    )
    support_agent_pb2_grpc.add_SupportAgentServicer_to_server(SupportAgentService(), server)
    health_service = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_service, server)
    health_service.set("", health_pb2.HealthCheckResponse.SERVING)
    health_service.set("gole.support.v1.SupportAgent", health_pb2.HealthCheckResponse.SERVING)
    server.add_insecure_port(f"0.0.0.0:{port}")
    server.start()
    LOGGER.info("GoLe support agent gRPC 시작 port=%s engine=rules-v1 external_model=false", port)

    def stop(*_args) -> None:
        health_service.enter_graceful_shutdown()
        server.stop(grace=5)

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
