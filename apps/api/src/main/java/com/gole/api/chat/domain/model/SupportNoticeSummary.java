package com.gole.api.chat.domain.model;

import java.time.Instant;

/** 보존 중인 전체 문의의 현황. 각 안내의 다섯 분류는 서로 겹치지 않는다. */
public record SupportNoticeSummary(
        Instant generatedAt,
        long total,
        Counts progress,
        Counts result,
        Counts activeProgress,
        Counts activeResult,
        long resolvedMissingProgress,
        long resolvedMissingResult) {
    public record Counts(long pending, long dueSoon, long overdue, long onTime, long late) {}
}
