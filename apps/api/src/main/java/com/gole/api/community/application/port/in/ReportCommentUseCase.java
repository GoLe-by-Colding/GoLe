package com.gole.api.community.application.port.in;

/** 저장된 댓글과 게시글 관계를 서버에서 검증한 뒤 신고한다. */
public interface ReportCommentUseCase {

    String report(ReportCommentCommand command);

    /** @param reasonCode 신고 컨텍스트의 사유 코드(웹 경계에서 검증된 값) */
    record ReportCommentCommand(String reporterId, String postId, String commentId, String reasonCode, String detail) {}
}
