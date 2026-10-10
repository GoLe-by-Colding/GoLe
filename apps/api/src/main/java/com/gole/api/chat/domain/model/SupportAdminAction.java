package com.gole.api.chat.domain.model;

/**
 * 문의 콘솔 조치 중 관리자 감사 로그에 남기는 것. 조치와 같은 트랜잭션에서 기록한다.
 *
 * <p>이름은 관리자 감사 로그의 조치 유형과 같다. 변환은 admin 어댑터가 한다.
 */
public enum SupportAdminAction {
    SUPPORT_ASSIGN,
    SUPPORT_TRANSFER,
    SUPPORT_TAKEOVER,
    SUPPORT_RESOLVE,
    SUPPORT_REOPEN,
    SUPPORT_REPLY,
    SUPPORT_INTERNAL_NOTE,
    SUPPORT_NOTIFICATION_REQUEUE
}
