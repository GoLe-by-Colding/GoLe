package com.gole.api.chat.domain.model;

import java.time.Instant;
import java.util.List;

public record ChatReportSnapshot(
        String id,
        String reportId,
        String roomId,
        String reportedMessageId,
        String reporterId,
        List<ChatReportSnapshotMessage> messages,
        Instant capturedAt) {}
