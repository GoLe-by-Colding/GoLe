package com.gole.api.admin.adapter.in.web;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.community.application.port.in.ModerateCommentUseCase;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.model.ReportTargetType;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신고된 댓글의 불변 원문과 부모 게시글을 운영자가 확인하는 최소 문맥 API. */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminCommentReportController {

    private final ManageReportsUseCase reports;
    private final ModerateCommentUseCase comments;
    private final RecordAdminActionUseCase audit;

    public AdminCommentReportController(
            ManageReportsUseCase reports, ModerateCommentUseCase comments, RecordAdminActionUseCase audit) {
        this.reports = reports;
        this.comments = comments;
        this.audit = audit;
    }

    @Operation(summary = "댓글 신고 문맥", description = "신고 대상 댓글 원문과 부모 게시글 ID만 반환하고 열람을 감사합니다.")
    @GetMapping("/{reportId}/comment-context")
    public CommentContext context(@PathVariable String reportId, HttpServletRequest http) {
        var report = reports.get(reportId);
        if (report.getTargetType() != ReportTargetType.COMMENT) {
            throw new BadRequestException("REPORT_NOT_COMMENT", "댓글 신고가 아닙니다");
        }
        Comment comment = comments.getForModeration(report.getTargetId());
        AdminActor actor = AdminActor.of(http);
        audit.record(new RecordAdminActionCommand(
                actor.id(),
                actor.email(),
                AdminActionType.COMMENT_REPORT_CONTEXT_VIEW,
                AdminTargetType.COMMENT,
                comment.id(),
                "신고 댓글 문맥 열람"));
        return CommentContext.from(comment);
    }

    public record CommentContext(
            String id, String postId, String authorId, String content, Instant createdAt, boolean hidden) {

        static CommentContext from(Comment comment) {
            return new CommentContext(
                    comment.id(),
                    comment.postId(),
                    comment.authorId(),
                    comment.content(),
                    comment.createdAt(),
                    comment.isHidden());
        }
    }
}
