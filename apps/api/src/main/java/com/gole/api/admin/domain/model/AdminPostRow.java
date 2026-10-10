package com.gole.api.admin.domain.model;

import java.time.Instant;

public record AdminPostRow(String id, String authorId, String content, String type, String status, Instant createdAt) {}
