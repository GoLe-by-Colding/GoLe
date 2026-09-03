package com.gole.api.community.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.exception.NotFoundException;
import com.gole.api.community.application.port.in.ReportCommentUseCase.ReportCommentCommand;
import com.gole.api.community.application.port.out.CommentRepositoryPort;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommentReportServiceTest {

    private final CommentRepositoryPort comments = mock(CommentRepositoryPort.class);
    private final SubmitReportUseCase reports = mock(SubmitReportUseCase.class);
    private final CommentReportService service = new CommentReportService(comments, reports);

    @Test
    void verifiesStoredParentBeforeSubmittingCommentReport() {
        Comment comment = new Comment("comment-1", "post-1", "author-1", "원문", Instant.EPOCH);
        when(comments.findById("comment-1")).thenReturn(Optional.of(comment));
        when(reports.submit(org.mockito.ArgumentMatchers.any())).thenReturn("report-1");

        String result = service.report(
                new ReportCommentCommand("reporter-1", "post-1", "comment-1", ReportReason.INAPPROPRIATE, "욕설"));

        assertThat(result).isEqualTo("report-1");
        ArgumentCaptor<SubmitReportCommand> command = ArgumentCaptor.forClass(SubmitReportCommand.class);
        verify(reports).submit(command.capture());
        assertThat(command.getValue().targetType()).isEqualTo(ReportTargetType.COMMENT);
        assertThat(command.getValue().targetId()).isEqualTo("comment-1");
        assertThat(command.getValue().reporterId()).isEqualTo("reporter-1");
    }

    @Test
    void rejectsMismatchedParentAndAlreadyHiddenComment() {
        Comment visible = new Comment("comment-1", "post-1", "author-1", "원문", Instant.EPOCH);
        when(comments.findById("comment-1")).thenReturn(Optional.of(visible));

        assertThatThrownBy(() -> service.report(
                        new ReportCommentCommand("reporter-1", "other-post", "comment-1", ReportReason.OTHER, "")))
                .isInstanceOf(NotFoundException.class);

        when(comments.findById("comment-1")).thenReturn(Optional.of(visible.hide("조치됨", Instant.EPOCH.plusSeconds(1))));
        assertThatThrownBy(() -> service.report(
                        new ReportCommentCommand("reporter-1", "post-1", "comment-1", ReportReason.OTHER, "")))
                .isInstanceOf(NotFoundException.class);
    }
}
