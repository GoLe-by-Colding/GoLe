package com.gole.api.common.operations;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Discord 등 외부 운영 채널로 전달되는 구조화 이벤트.
 *
 * <p>비밀번호, 세션 토큰, 이메일, 결제키 등 민감정보는 fields에 넣지 않는다.
 */
public record OperationalEvent(
        Category category,
        Level level,
        String title,
        String description,
        Map<String, String> fields,
        Instant occurredAt) {

    public OperationalEvent {
        fields = Map.copyOf(fields == null ? Map.of() : new LinkedHashMap<>(fields));
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public enum Category {
        ACCOUNT,
        PAYMENT,
        SUPPORT,
        ADMIN,
        APPLICATION
    }

    public enum Level {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
