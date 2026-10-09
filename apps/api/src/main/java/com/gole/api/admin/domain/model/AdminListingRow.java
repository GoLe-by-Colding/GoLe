package com.gole.api.admin.domain.model;

import java.time.Instant;

public record AdminListingRow(
        String id, String title, String sellerId, long price, String status, String category, Instant createdAt) {}
