package com.gole.api.chat.domain.model;

import java.time.Instant;

public record SupportPurgeReceipt(
        String receiptId,
        String actorId,
        String reasonCode,
        String idempotencyKeyHash,
        String requestFingerprint,
        Instant resolvedAt,
        Instant purgedAt,
        SupportPurgeCounts counts) {}
