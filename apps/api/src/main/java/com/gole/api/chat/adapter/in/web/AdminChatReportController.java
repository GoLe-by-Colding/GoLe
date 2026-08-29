package com.gole.api.chat.adapter.in.web;

import com.gole.api.admin.adapter.in.web.AdminActor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.StoredSnapshot;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.model.ReportTargetType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자는 신고에 고정된 문맥만 열람한다. 살아 있는 채팅방 접근권은 부여하지 않는다. */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminChatReportController {

    private final ManageReportsUseCase reports;
    private final ChatReportSnapshotPort snapshots;
    private final RecordAdminActionUseCase audit;

    public AdminChatReportController(
            ManageReportsUseCase reports, ChatReportSnapshotPort snapshots, RecordAdminActionUseCase audit) {
        this.reports = reports;
        this.snapshots = snapshots;
        this.audit = audit;
    }

    @GetMapping("/{reportId}/chat-snapshot")
    public StoredSnapshot snapshot(@PathVariable String reportId, HttpServletRequest http) {
        var report = reports.get(reportId);
        if (report.getTargetType() != ReportTargetType.CHAT_MESSAGE) {
            throw new BadRequestException("REPORT_NOT_CHAT_MESSAGE", "채팅 메시지 신고가 아닙니다");
        }
        StoredSnapshot snapshot = snapshots
                .findByReportId(reportId)
                .orElseThrow(() -> new NotFoundException("CHAT_REPORT_SNAPSHOT_NOT_FOUND", "신고 스냅샷을 찾을 수 없습니다"));
        AdminActor actor = AdminActor.of(http);
        audit.record(new RecordAdminActionCommand(
                actor.id(),
                actor.email(),
                AdminActionType.CHAT_REPORT_SNAPSHOT_VIEW,
                AdminTargetType.CHAT_REPORT_SNAPSHOT,
                snapshot.id(),
                "reportId=" + reportId));
        return snapshot;
    }
}
