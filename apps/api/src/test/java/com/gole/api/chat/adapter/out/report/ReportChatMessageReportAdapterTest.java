package com.gole.api.chat.adapter.out.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportChatMessageReportAdapterTest {

    private final SubmitReportUseCase reports = mock(SubmitReportUseCase.class);
    private final ReportChatMessageReportAdapter adapter = new ReportChatMessageReportAdapter(reports);

    @Test
    @DisplayName("채팅 메시지 대상과 사유 코드로 신고를 접수한다")
    void submit_mapsToChatMessageReport() {
        SubmitReportCommand expected = new SubmitReportCommand(
                "reporter-1", ReportTargetType.CHAT_MESSAGE, "message-12", ReportReason.INAPPROPRIATE, "욕설 메시지");
        when(reports.submit(expected)).thenReturn("report-1");

        assertThat(adapter.submit("reporter-1", "message-12", "INAPPROPRIATE", "욕설 메시지"))
                .isEqualTo("report-1");
        verify(reports).submit(expected);
    }
}
