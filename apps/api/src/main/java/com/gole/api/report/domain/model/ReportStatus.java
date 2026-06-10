package com.gole.api.report.domain.model;

/** 신고 처리 상태. */
public enum ReportStatus {
    /** 접수됨(미처리) */
    PENDING,
    /** 조치 완료(매물 내림/게시글 삭제 등) */
    RESOLVED,
    /** 기각(문제 없음) */
    DISMISSED
}
