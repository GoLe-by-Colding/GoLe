package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.Objects;

/** 채팅 메시지. 불변 값(이벤트). */
public record ChatMessage(
        String id,
        String roomId,
        String senderId,
        String content,
        Instant sentAt) {

    public ChatMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(sentAt, "sentAt");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
