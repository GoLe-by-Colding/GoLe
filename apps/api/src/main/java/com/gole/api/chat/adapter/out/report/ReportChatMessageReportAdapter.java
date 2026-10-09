package com.gole.api.chat.adapter.out.report;

import com.gole.api.chat.application.port.out.ChatMessageReportPort;
import com.gole.api.report.application.port.in.SubmitReportUseCase;
import com.gole.api.report.application.port.in.SubmitReportUseCase.SubmitReportCommand;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import org.springframework.stereotype.Component;

/** 신고 컨텍스트 통합 어댑터. 채팅 메시지 신고를 report 의 접수 유스케이스로 넘긴다. */
@Component
public class ReportChatMessageReportAdapter implements ChatMessageReportPort {

    private final SubmitReportUseCase reports;

    public ReportChatMessageReportAdapter(SubmitReportUseCase reports) {
        this.reports = reports;
    }

    @Override
    public String submit(String reporterId, String messageId, String reasonCode, String detail) {
        return reports.submit(new SubmitReportCommand(
                reporterId, ReportTargetType.CHAT_MESSAGE, messageId, ReportReason.valueOf(reasonCode), detail));
    }
}
