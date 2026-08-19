package com.gole.api.admin.domain.model;

/**
 * 감사 대상 관리자 조치 유형. (admin-console 요구사항 8.1)
 *
 * <p>상태를 바꾸는 조치만 열거한다. 단순 조회는 감사 대상이 아니다.
 */
public enum AdminActionType {
    LISTING_TAKEDOWN,
    POST_REMOVE,
    ACCOUNT_SUSPEND,
    ACCOUNT_REINSTATE,
    ACCOUNT_ROLE_CHANGE,
    REPORT_RESOLVE,
    REPORT_DISMISS,
    CATALOG_SET_CREATE,
    CATALOG_SET_UPDATE,
    CATALOG_SET_FEATURE,
    ORDER_PAYMENT_RECONCILE,
    ORDER_DISPUTE_RESOLVE,
    /** 개인정보 열람도 조치다 — 운영자의 전체 연락처 열람 감사 기록(R8.5). */
    ORDER_CONTACT_VIEW,
    SETTLEMENT_MARK_PAID
}
