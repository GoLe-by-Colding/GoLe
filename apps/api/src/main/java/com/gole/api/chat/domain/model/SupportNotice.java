package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.Objects;

/** 실제 답변의 참조만 보존한다. 본문·이메일을 복제하거나 읽음/법정 준수를 주장하지 않는다. */
public record SupportNotice(SupportNoticeKind kind, String messageId, String senderId, Instant sentAt) {
    public SupportNotice {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(sentAt, "sentAt");
    }
}
