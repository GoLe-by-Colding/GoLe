from concurrent import futures

import grpc

from gole.support.v1 import support_agent_pb2, support_agent_pb2_grpc
from gole_support_agent.server import SupportAgentService


def test_grpc_contract_returns_human_review_draft():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=1))
    support_agent_pb2_grpc.add_SupportAgentServicer_to_server(SupportAgentService(), server)
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
            response = support_agent_pb2_grpc.SupportAgentStub(channel).Analyze(
                support_agent_pb2.AnalyzeSupportRequest(
                    ticket_id="ticket-grpc-1",
                    declared_category=support_agent_pb2.SUPPORT_CATEGORY_PRODUCT_FEEDBACK,
                    title="기능 개선 제안",
                    message="컬렉션 공유 기능을 개선해 주세요.",
                    locale="ko-KR",
                ),
                timeout=2,
            )
    finally:
        server.stop(grace=None).wait()

    assert response.recommended_category == support_agent_pb2.SUPPORT_CATEGORY_PRODUCT_FEEDBACK
    assert response.priority == support_agent_pb2.SUPPORT_PRIORITY_NORMAL
    assert response.human_review_required is True
    assert response.external_model_used is False
    assert response.engine_version == "rules-v1"
    assert "컬렉션 공유 기능" not in response.summary
    assert "컬렉션 공유 기능" not in response.draft_reply
