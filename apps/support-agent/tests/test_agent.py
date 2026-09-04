from gole_support_agent.agent import analyze_support


def test_payment_inquiry_is_high_priority_and_never_auto_sent():
    result = analyze_support(
        ticket_id="ticket-1",
        declared_category="GENERAL",
        title="환불 문의",
        message="결제 취소가 필요합니다.",
    )

    assert result["recommended_category"] == "PAYMENT"
    assert result["priority"] == "HIGH"
    assert result["risk_flags"] == ["PAYMENT_REVIEW"]
    assert result["human_review_required"] is True
    assert result["external_model_used"] is False
    assert "결제 취소가 필요합니다" not in result["summary"]
    assert "결제 취소가 필요합니다" not in result["draft_reply"]


def test_privacy_request_stays_human_review_only():
    result = analyze_support(
        ticket_id="ticket-2",
        declared_category="PRIVACY_CORRECTION_DELETION",
        title="계정 삭제",
        message="개인정보 삭제를 요청합니다.",
    )

    assert result["recommended_category"] == "PRIVACY_CORRECTION_DELETION"
    assert result["priority"] == "HIGH"
    assert "PRIVACY_REVIEW" in result["risk_flags"]
    assert result["human_review_required"] is True


def test_urgent_risk_upgrades_priority_without_echoing_identity():
    result = analyze_support(
        ticket_id="ticket-3",
        declared_category="TRADE",
        title="사기 신고",
        message="제 이름은 홍길동이고 계정을 도용당했습니다.",
    )

    assert result["priority"] == "URGENT"
    assert "URGENT_HUMAN_REVIEW" in result["risk_flags"]
    assert "홍길동" not in result["summary"]
    assert "홍길동" not in result["draft_reply"]
