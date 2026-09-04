package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.gole.api.chat.application.SupportNotificationOutboxAdminService;
import com.gole.api.chat.application.SupportNotificationOutboxAdminService.RequeueOutcome;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportNotificationEvent;
import com.gole.api.chat.domain.model.SupportNotificationEvent.EventType;
import com.gole.api.chat.domain.model.SupportNotificationEvent.State;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminSupportNotificationControllerTest {

    private static final String EVENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    private final SupportNotificationOutboxAdminService notifications =
            mock(SupportNotificationOutboxAdminService.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AdminSupportNotificationController(notifications, audit))
            .addInterceptors(new AdminAuthInterceptor(sessions, new SessionCookie("false")))
            .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
            .build();

    @BeforeEach
    void sessions() {
        when(sessions.resolve("admin-token"))
                .thenReturn(Optional.of(new CurrentSession("admin-1", "admin@gole.test", Role.ADMIN)));
        when(sessions.resolve("user-token"))
                .thenReturn(Optional.of(new CurrentSession("user-1", "user@gole.test", Role.USER)));
    }

    @Test
    void adminCanRequeueOneDeadLetterWithStructuredAudit() throws Exception {
        SupportNotificationEvent pending = new SupportNotificationEvent(
                EVENT_ID,
                EventType.OPENED,
                SupportCategory.GENERAL,
                SupportStatus.UNASSIGNED,
                State.PENDING,
                0,
                NOW,
                null,
                null,
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                null);
        when(notifications.requeue(
                        EVENT_ID,
                        "REQUEUE:" + EVENT_ID,
                        SupportNotificationOutboxAdminService.RequeueReasonCode.WEBHOOK_CONFIGURATION_RESTORED))
                .thenReturn(new RequeueOutcome(pending, true));

        mvc.perform(post("/api/admin/support-notifications/{eventId}/requeue", EVENT_ID)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "confirmation":"REQUEUE:%s",
                                  "reasonCode":"WEBHOOK_CONFIGURATION_RESTORED"
                                }
                                """
                                        .formatted(EVENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.changed").value(true));

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        assertThat(command.getValue().type()).isEqualTo(AdminActionType.SUPPORT_NOTIFICATION_REQUEUE);
        assertThat(command.getValue().targetId()).isEqualTo(EVENT_ID);
        assertThat(command.getValue().reason()).isEqualTo("reasonCode=WEBHOOK_CONFIGURATION_RESTORED");
    }

    @Test
    void nonAdminCannotReachRecoveryService() throws Exception {
        mvc.perform(post("/api/admin/support-notifications/{eventId}/requeue", EVENT_ID)
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "confirmation":"REQUEUE:%s",
                                  "reasonCode":"DISCORD_INCIDENT_RESOLVED"
                                }
                                """
                                        .formatted(EVENT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));

        verify(notifications, never()).requeue(any(), any(), any());
        verify(audit, never()).record(any());
    }
}
