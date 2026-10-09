package com.gole.api.chat.domain.model;

import java.time.Instant;

public record SupportRetentionHold(
        String roomId,
        String holdReference,
        boolean active,
        String reasonCode,
        String placedBy,
        Instant placedAt,
        String releasedBy,
        Instant releasedAt,
        String releaseReasonCode,
        long version) {}
