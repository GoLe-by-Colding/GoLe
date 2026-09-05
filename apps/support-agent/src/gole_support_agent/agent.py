from __future__ import annotations

import re
from typing import TypedDict

from langgraph.graph import END, START, StateGraph

ENGINE_VERSION = "rules-v1"

GENERAL = "GENERAL"
TRADE = "TRADE"
PAYMENT = "PAYMENT"
PRODUCT_FEEDBACK = "PRODUCT_FEEDBACK"
PRIVACY_CATEGORIES = {
    "PRIVACY_ACCESS",
    "PRIVACY_CORRECTION_DELETION",
    "PRIVACY_PROCESSING_STOP",
}
SUPPORTED_CATEGORIES = {GENERAL, TRADE, PAYMENT, PRODUCT_FEEDBACK, *PRIVACY_CATEGORIES}


class SupportState(TypedDict, total=False):
    ticket_id: str
    declared_category: str
    title: str
    message: str
    locale: str
    normalized_text: str
    recommended_category: str
    priority: str
    summary: str
    draft_reply: str
    risk_flags: list[str]
    human_review_required: bool
    external_model_used: bool
    engine_version: str


def _normalize(state: SupportState) -> SupportState:
    text = f"{state.get('title', '')} {state.get('message', '')}".lower()
    return {"normalized_text": re.sub(r"\s+", " ", text).strip()}


def _classify(state: SupportState) -> SupportState:
    declared = state.get("declared_category", GENERAL)
    if declared not in SUPPORTED_CATEGORIES:
        declared = GENERAL
    text = state.get("normalized_text", "")

    category = declared
    if any(keyword in text for keyword in ("개인정보", "탈퇴", "삭제 요청", "privacy")):
        category = declared if declared in PRIVACY_CATEGORIES else "PRIVACY_ACCESS"
    elif any(keyword in text for keyword in ("결제", "환불", "정산", "돈", "payment", "refund")):
        category = PAYMENT
    elif any(keyword in text for keyword in ("거래", "판매", "구매", "배송", "매물", "trade", "shipping")):
        category = TRADE
    elif any(keyword in text for keyword in ("피드백", "개선", "오류", "버그", "제안", "feedback", "bug")):
        category = PRODUCT_FEEDBACK

    risk_flags: list[str] = []
    priority = "NORMAL"
    if category == PAYMENT:
        risk_flags.append("PAYMENT_REVIEW")
        priority = "HIGH"
    if category in PRIVACY_CATEGORIES:
        risk_flags.append("PRIVACY_REVIEW")
        priority = "HIGH"
    if any(keyword in text for keyword in ("사기", "도용", "해킹", "고소", "긴급", "fraud", "hacked")):
        risk_flags.append("URGENT_HUMAN_REVIEW")
        priority = "URGENT"

    return {
        "recommended_category": category,
        "priority": priority,
        "risk_flags": risk_flags,
        "human_review_required": True,
        "external_model_used": False,
        "engine_version": ENGINE_VERSION,
    }


def _draft(state: SupportState) -> SupportState:
    category = state["recommended_category"]
    category_label = {
        GENERAL: "일반 문의",
        TRADE: "거래 문의",
        PAYMENT: "결제 문의",
        PRODUCT_FEEDBACK: "제품 피드백",
        "PRIVACY_ACCESS": "개인정보 열람 요청",
        "PRIVACY_CORRECTION_DELETION": "개인정보 정정·삭제 요청",
        "PRIVACY_PROCESSING_STOP": "개인정보 처리정지 요청",
    }[category]
    caution = (
        " 결제 상태와 원장을 담당자가 직접 확인한 뒤 안내드리겠습니다."
        if category == PAYMENT
        else " 본인 확인과 법정 보존 항목을 담당자가 검토한 뒤 안내드리겠습니다."
        if category in PRIVACY_CATEGORIES
        else " 담당자가 내용을 확인한 뒤 이 대화에서 안내드리겠습니다."
    )
    return {
        # 사용자 원문이나 식별자를 되비추지 않아 관리자 목록·로그의 PII 확산을 막는다.
        "summary": f"{category_label}가 접수되어 사람 검토가 필요함.",
        "draft_reply": f"{category_label}로 문의가 정상 접수되었습니다.{caution}",
    }


def build_graph():
    graph = StateGraph(SupportState)
    graph.add_node("normalize", _normalize)
    graph.add_node("classify", _classify)
    graph.add_node("draft", _draft)
    graph.add_edge(START, "normalize")
    graph.add_edge("normalize", "classify")
    graph.add_edge("classify", "draft")
    graph.add_edge("draft", END)
    return graph.compile()


SUPPORT_GRAPH = build_graph()


def analyze_support(
    *, ticket_id: str, declared_category: str, title: str, message: str, locale: str = "ko-KR"
) -> SupportState:
    return SUPPORT_GRAPH.invoke(
        {
            "ticket_id": ticket_id,
            "declared_category": declared_category,
            "title": title,
            "message": message,
            "locale": locale,
        }
    )
