package com.gole.api.chat.domain.model;

import java.time.Instant;

public record ChatReportSnapshotMessage(String messageId, String senderId, String content, Instant sentAt) {}
