package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gole.api.account.adapter.in.web.SessionCookie;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.admin.adapter.in.web.AdminAuthInterceptor;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase;
import com.gole.api.admin.application.port.in.RecordAdminActionUseCase.RecordAdminActionCommand;
import com.gole.api.admin.domain.model.AdminActionType;
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.SnapshotMessage;
import com.gole.api.chat.application.port.out.ChatReportSnapshotPort.StoredSnapshot;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import com.gole.api.report.application.port.in.ManageReportsUseCase;
import com.gole.api.report.domain.model.Report;
import com.gole.api.report.domain.model.ReportReason;
import com.gole.api.report.domain.model.ReportTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminChatReportControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    private final ManageReportsUseCase reports = mock(ManageReportsUseCase.class);
    private final ChatReportSnapshotPort snapshots = mock(ChatReportSnapshotPort.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AdminChatReportController(reports, snapshots, audit))
            .addInterceptors(new AdminAuthInterceptor(sessions, new SessionCookie("false")))
            .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
            .build();

    @BeforeEach
    void adminSession() {
        when(sessions.resolve("admin-token"))
                .thenReturn(Optional.of(new CurrentSession("admin-1", "admin@gole.test", Role.ADMIN)));
        when(sessions.resolve("user-token"))
                .thenReturn(Optional.of(new CurrentSession("user-1", "user@gole.test", Role.USER)));
        when(sessions.resolve("")).thenReturn(Optional.empty());
    }

    @Test
    void adminCanReadOnlyTheFixedSnapshotAndViewIsAudited() throws Exception {
        StoredSnapshot stored = snapshot();
        when(reports.get("report-1")).thenReturn(report(ReportTargetType.CHAT_MESSAGE));
        when(snapshots.findByReportId("report-1")).thenReturn(Optional.of(stored));

        mvc.perform(get("/api/admin/reports/report-1/chat-snapshot").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("snapshot-1"))
                .andExpect(jsonPath("$.reportId").value("report-1"))
                .andExpect(jsonPath("$.reportedMessageId").value("message-1"))
                .andExpect(jsonPath("$.messages[0].content").value("서버에 고정된 문맥"));

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new RecordAdminActionCommand(
                        "admin-1",
                        "admin@gole.test",
                        AdminActionType.CHAT_REPORT_SNAPSHOT_VIEW,
                        AdminTargetType.CHAT_REPORT_SNAPSHOT,
                        "snapshot-1",
                        "reportId=report-1"));
    }

    @Test
    void nonChatReportCannotOpenChatSnapshotOrWriteAudit() throws Exception {
        when(reports.get("report-1")).thenReturn(report(ReportTargetType.POST));

        mvc.perform(get("/api/admin/reports/report-1/chat-snapshot").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_CHAT_MESSAGE"));

        verify(snapshots, never()).findByReportId(any());
        verify(audit, never()).record(any());
    }

    @Test
    void missingSnapshotDoesNotProduceFalseAuditEvidence() throws Exception {
        when(reports.get("report-1")).thenReturn(report(ReportTargetType.CHAT_MESSAGE));
        when(snapshots.findByReportId("report-1")).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/reports/report-1/chat-snapshot").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_REPORT_SNAPSHOT_NOT_FOUND"));

        verify(audit, never()).record(any());
    }

    @Test
    void unauthenticatedAndRegularUsersCannotReadSnapshot() throws Exception {
        mvc.perform(get("/api/admin/reports/report-1/chat-snapshot"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION"));

        mvc.perform(get("/api/admin/reports/report-1/chat-snapshot").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));

        verify(reports, never()).get(any());
        verify(snapshots, never()).findByReportId(any());
        verify(audit, never()).record(any());
    }

    private static Report report(ReportTargetType targetType) {
        return Report.submit(
                "report-1",
                "reporter-1",
                targetType,
                targetType == ReportTargetType.CHAT_MESSAGE ? "message-1" : "post-1",
                ReportReason.INAPPROPRIATE,
                "신고 상세",
                NOW);
    }

    private static StoredSnapshot snapshot() {
        return new StoredSnapshot(
                "snapshot-1",
                "report-1",
                "room-1",
                "message-1",
                "reporter-1",
                List.of(new SnapshotMessage("message-1", "sender-1", "서버에 고정된 문맥", NOW.minusSeconds(60))),
                NOW);
    }
}
