package com.gole.api.admin.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.admin.application.port.out.ContentModerationPort;
import com.gole.api.admin.application.port.out.ReportCasePort;
import com.gole.api.admin.application.port.out.ReportCasePort.ReportCase;
import com.gole.api.admin.application.port.out.ReportCasePort.TargetKind;
import com.gole.api.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolveReportTargetServiceTest {

    private final ReportCasePort reports = mock(ReportCasePort.class);
    private final ContentModerationPort moderation = mock(ContentModerationPort.class);
    private final ResolveReportTargetService service = new ResolveReportTargetService(reports, moderation);

    @Test
    @DisplayName("게시글 신고는 게시글을 지우고 신고를 완료한다")
    void resolve_removesPostThenMarksResolved() {
        when(reports.find("report-1")).thenReturn(new ReportCase("report-1", true, TargetKind.POST, "post-1"));

        service.resolve("report-1", "스팸");

        verify(moderation).removePost("post-1", "스팸");
        verify(reports).markResolved("report-1");
    }

    @Test
    @DisplayName("채팅 메시지 등 콘텐츠 조치 대상이 아니면 아무것도 바꾸지 않는다")
    void resolve_rejectsNonContentTargets() {
        when(reports.find("report-1")).thenReturn(new ReportCase("report-1", true, TargetKind.OTHER, "message-1"));

        assertThatThrownBy(() -> service.resolve("report-1", "욕설"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("CHAT_REPORT_MANUAL_ACTION_REQUIRED");
        verifyNoInteractions(moderation);
        verify(reports, never()).markResolved(anyString());
    }
}
