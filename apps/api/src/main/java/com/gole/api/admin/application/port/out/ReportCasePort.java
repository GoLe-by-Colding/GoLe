package com.gole.api.admin.application.port.out;

/** Outbound port: 신고 건 조회와 완료 처리. */
public interface ReportCasePort {

    /** 신고가 없으면 신고 컨텍스트의 404 예외를 그대로 던진다. */
    ReportCase find(String reportId);

    void markResolved(String reportId);

    record ReportCase(String id, boolean pending, TargetKind targetKind, String targetId) {}

    /** 관리자가 콘텐츠 조치로 처리할 수 있는 신고 대상. 그 밖(채팅 메시지 등)은 {@code OTHER}. */
    enum TargetKind {
        LISTING,
        POST,
        COMMENT,
        REVIEW,
        OTHER
    }
}
