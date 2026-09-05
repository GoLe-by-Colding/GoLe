package com.gole.api.account.domain.model;

/** 자동 파기를 금지하고 운영 검토를 요구하는 보존·수명주기 조건. */
public enum AccountDeletionBlocker {
    ACTIVE_ORDER,
    UNSETTLED_PAYOUT,
    PENDING_REPORT,
    SUPPORT_RECORDS_REQUIRE_PURGE,
    PUBLIC_CONTENT_REQUIRES_LIFECYCLE_REVIEW,
    MEDIA_REQUIRES_LIFECYCLE_REVIEW,
    OWNED_GROUP_REQUIRES_TRANSFER,
    EXPLICIT_RETENTION_HOLD
}
