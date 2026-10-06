package com.gole.api.notification.domain.model;

/**
 * 알림 종류. (알림 스펙 N1)
 */
public enum NotificationType {
    ORDER_PLACED,
    ORDER_PAID,
    COMMENT,
    POST_LIKED,
    FOLLOW,
    NEW_LISTING,
    /** 관심·보유 세트의 단종 상태 변경(단종 임박·단종). */
    SET_RETIREMENT,
    /** 관심 세트에 새 매물이 등록됨. */
    WATCHED_SET_LISTING,
    GENERAL
}
