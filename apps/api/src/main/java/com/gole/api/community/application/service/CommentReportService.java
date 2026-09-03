package com.gole.api.community.application.service;

import com.gole.api.common.exception.NotFoundException;
import com.gole.api.community.application.port.in.ReportCommentUseCase;
import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportTargetType;
import org.springframework.stereotype.Service;

/** 댓글 신고는 클라이언트가 주장한 대상이 아니라 저장된 댓글과 게시글 관계를 검증해 접수한다. */
@Service
public class CommentReportService implements ReportCommentUseCase {

    private final CommentRepositoryPort comments;
    private final SubmitReportUseCase reports;

    public CommentReportService(CommentRepositoryPort comments, SubmitReportUseCase reports) {
        this.comments = comments;
        this.reports = reports;
    }

    @Override
    public String report(ReportCommentCommand command) {
        Comment comment = comments.findById(command.commentId())
                .filter(candidate -> candidate.postId().equals(command.postId()))
                .filter(candidate -> !candidate.isHidden())
                .orElseThrow(() -> new NotFoundException("COMMENT_NOT_FOUND", "신고할 댓글을 찾을 수 없습니다"));
        return reports.submit(new SubmitReportCommand(
                command.reporterId(), ReportTargetType.COMMENT, comment.id(), command.reason(), command.detail()));
    }
}
