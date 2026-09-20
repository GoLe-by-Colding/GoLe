package com.gole.api.common.operations.sentry;

import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import java.util.List;

/** 원본 필드의 삭제 목록 대신 새 객체에 고정 진단 코드만 투영한다. */
public final class SentrySafeEvents {
    private SentrySafeEvents() {}

    public static SentryEvent project(SentryEvent original) {
        if (original.getLevel() != SentryLevel.ERROR && original.getLevel() != SentryLevel.FATAL) return null;
        SentryEvent safe = new SentryEvent();
        safe.setLevel(original.getLevel());
        safe.setEnvironment("production");
        Message message = new Message();
        message.setMessage("UNEXPECTED_ERROR");
        safe.setMessage(message);
        safe.setTag("component", "api");
        safe.setTag("diagnostic", "UNEXPECTED_ERROR");
        safe.setFingerprints(List.of("gole", "api", "UNEXPECTED_ERROR"));
        return safe;
    }
}
