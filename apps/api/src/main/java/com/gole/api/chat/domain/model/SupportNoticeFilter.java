package com.gole.api.chat.domain.model;

/** 해결 여부와 독립적으로 안내 기록이 없는 문의를 찾는 운영 큐. */
public enum SupportNoticeFilter {
    PROGRESS_DUE_SOON(SupportNoticeKind.PROGRESS, false),
    PROGRESS_OVERDUE(SupportNoticeKind.PROGRESS, true),
    RESULT_DUE_SOON(SupportNoticeKind.RESULT, false),
    RESULT_OVERDUE(SupportNoticeKind.RESULT, true),
    PROGRESS_MISSING(SupportNoticeKind.PROGRESS, false),
    RESULT_MISSING(SupportNoticeKind.RESULT, false);

    private final SupportNoticeKind kind;
    private final boolean overdue;

    SupportNoticeFilter(SupportNoticeKind kind, boolean overdue) {
        this.kind = kind;
        this.overdue = overdue;
    }

    public SupportNoticeKind kind() {
        return kind;
    }

    public boolean overdue() {
        return overdue;
    }

    public boolean missingOnly() {
        return this == PROGRESS_MISSING || this == RESULT_MISSING;
    }
}
