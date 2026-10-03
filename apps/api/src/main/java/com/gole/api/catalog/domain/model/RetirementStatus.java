package com.gole.api.catalog.domain.model;

/**
 * LEGO 세트 단종 상태. (요구사항 4: Catalog)
 */
public enum RetirementStatus {
    ACTIVE,
    /** 단종 임박. 관리자가 수동으로 전환하며, 관심 사용자에게 알림이 간다. */
    RETIRING_SOON,
    RETIRED
}
