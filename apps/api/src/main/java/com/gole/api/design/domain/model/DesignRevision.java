package com.gole.api.design.domain.model;

import java.time.Instant;
import java.util.Map;

/** 하나의 Mongo 문서로 원자적으로 저장하는 불변 게시·감사 이벤트. */
public record DesignRevision(
        long revision, Map<String, String> tokens, String actorId, String reason, String action, Instant publishedAt) {
    public DesignRevision {
        tokens = Map.copyOf(tokens);
    }

    public static DesignRevision initial() {
        return new DesignRevision(0, DesignSchema.defaults(), "", "", "DEFAULT", Instant.EPOCH);
    }
}
