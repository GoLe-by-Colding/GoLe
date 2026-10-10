package com.gole.api.chat.domain.model;

import java.time.Instant;

public record SupportInternalNote(String id, String roomId, String authorId, String note, Instant createdAt) {}
