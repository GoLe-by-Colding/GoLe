package com.gole.api.community.application.port.out;

/** Outbound port: 댓글 신고 접수. */
public interface CommentReportPort {

    /**
     * @param reasonCode 신고 컨텍스트의 사유 코드(웹 경계에서 검증된 값)
     * @return 접수된 신고 id
     */
    String submit(String reporterId, String commentId, String reasonCode, String detail);
}
