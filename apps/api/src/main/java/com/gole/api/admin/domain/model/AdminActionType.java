package com.gole.api.admin.domain.model;

/**
 * 감사 대상 관리자 조치 유형. (admin-console 요구사항 8.1)
 *
 * <p>상태를 바꾸는 조치만 열거한다. 단순 조회는 감사 대상이 아니다.
 */
public enum AdminActionType {
    LISTING_TAKEDOWN,
    POST_REMOVE,
    COMMENT_HIDE,
    ACCOUNT_SUSPEND,
    ACCOUNT_REINSTATE,
    ACCOUNT_ROLE_CHANGE,
    REPORT_RESOLVE,
    REPORT_DISMISS,
    REVIEW_HIDE,
    CATALOG_SET_CREATE,
    CATALOG_SET_UPDATE,
    CATALOG_SET_FEATURE,
    ORDER_PAYMENT_RECONCILE,
    ORDER_DISPUTE_RESOLVE,
    /** 개인정보 열람도 조치다 — 운영자의 전체 연락처 열람 감사 기록(R8.5). */
    ORDER_CONTACT_VIEW,
    SETTLEMENT_CLAIM,
    SETTLEMENT_RECONCILE,
    SETTLEMENT_RECOVER,
    SETTLEMENT_MARK_PAID,
    SUPPORT_ASSIGN,
    SUPPORT_TRANSFER,
    SUPPORT_TAKEOVER,
    SUPPORT_REPLY,
    SUPPORT_RESOLVE,
    SUPPORT_REOPEN,
    SUPPORT_INTERNAL_NOTE,
    /** 신고에 고정된 채팅 문맥 열람. 실시간 방 접근과 구분해 별도 감사한다. */
    CHAT_REPORT_SNAPSHOT_VIEW,
    /** 신고된 공개 댓글 원문 열람. 블라인드 후에도 원문 접근을 추적한다. */
    COMMENT_REPORT_CONTEXT_VIEW,
    /** 서비스 공개 단계·기능 개방 변경. 서비스 전체를 열고 닫는 조치다. */
    LAUNCH_STAGE_CHANGE,
    /** 결제·정산 개방 전 사업·법무·실거래 준비 확인 또는 확인 취소. */
    LAUNCH_READINESS_CHANGE
}
