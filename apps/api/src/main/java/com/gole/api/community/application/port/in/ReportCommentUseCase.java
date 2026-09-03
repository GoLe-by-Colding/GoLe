package com.gole.api.community.application.port.in;

import com.gole.api.report.domain.model.ReportReason;

/** 저장된 댓글과 게시글 관계를 서버에서 검증한 뒤 신고한다. */
public interface ReportCommentUseCase {

    String report(ReportCommentCommand command);

    record ReportCommentCommand(
            String reporterId, String postId, String commentId, ReportReason reason, String detail) {}
}
