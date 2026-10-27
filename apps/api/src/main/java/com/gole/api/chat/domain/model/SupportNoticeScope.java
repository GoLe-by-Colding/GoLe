package com.gole.api.chat.domain.model;

/** 현재 처리할 미해결 문의와 과거 해결 문의의 기록 확인을 분리한다. */
public enum SupportNoticeScope {
    ALL,
    ACTIVE,
    RESOLVED
}
