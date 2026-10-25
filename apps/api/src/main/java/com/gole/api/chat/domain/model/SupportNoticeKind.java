package com.gole.api.chat.domain.model;

import java.time.Duration;
import java.time.Instant;

/** 고객에게 실제로 저장한 안내의 목적. 일반 답변·내부 메모와 구분한다. */
public enum SupportNoticeKind {
    PROGRESS(Duration.ofDays(3), Duration.ofDays(1)),
    RESULT(Duration.ofDays(10), Duration.ofDays(2));

    private final Duration targetAfter;
    private final Duration warningWindow;

    SupportNoticeKind(Duration targetAfter, Duration warningWindow) {
        this.targetAfter = targetAfter;
        this.warningWindow = warningWindow;
    }

    public Instant dueAt(Instant createdAt) {
        return createdAt.plus(targetAfter);
    }

    public Duration targetAfter() {
        return targetAfter;
    }

    public Duration warningWindow() {
        return warningWindow;
    }
}
