package com.gole.api.order.domain.model;

/** 결제 검증 순간에 확정해 주문에 불변 저장하는 금전 결제 증빙 등급. */
public enum PaymentEvidenceKind {
    /** PortOne LIVE 채널의 원장에서 검증된 실제 결제. */
    LIVE,
    /** PortOne TEST 채널 또는 로컬·E2E 스텁 결제. */
    TEST,
    /** 증빙 필드 도입 전 주문 등 결제 환경을 확인할 수 없는 레거시. */
    UNVERIFIED
}
