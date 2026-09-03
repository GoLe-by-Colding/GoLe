package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.community.application.port.in.ModerateCommentUseCase;
import com.gole.api.community.domain.model.Comment;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminCommentReportControllerTest {

    private final ManageReportsUseCase reports = mock(ManageReportsUseCase.class);
    private final ModerateCommentUseCase comments = mock(ModerateCommentUseCase.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final AdminCommentReportController controller = new AdminCommentReportController(reports, comments, audit);

    @Test
    void returnsImmutableCommentContextAndAuditsTheView() {
        when(reports.get("report-1")).thenReturn(report(ReportTargetType.COMMENT, "comment-1"));
        when(comments.getForModeration("comment-1"))
                .thenReturn(new Comment("comment-1", "post-1", "author-1", "원문", Instant.EPOCH));

        var result = controller.context("report-1", new MockHttpServletRequest());

        assertThat(result.postId()).isEqualTo("post-1");
        assertThat(result.content()).isEqualTo("원문");
        assertThat(result.hidden()).isFalse();
        verify(audit).record(any());
    }

    @Test
    void rejectsNonCommentReportWithoutReadingTarget() {
        when(reports.get("report-1")).thenReturn(report(ReportTargetType.POST, "post-1"));

        assertThatThrownBy(() -> controller.context("report-1", new MockHttpServletRequest()))
                .isInstanceOf(BadRequestException.class);
    }

    private static Report report(ReportTargetType type, String targetId) {
        return Report.submit("report-1", "reporter-1", type, targetId, ReportReason.INAPPROPRIATE, "", Instant.EPOCH);
    }
}
