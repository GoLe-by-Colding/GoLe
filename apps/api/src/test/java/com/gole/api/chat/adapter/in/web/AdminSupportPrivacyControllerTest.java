package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.gole.api.chat.application.SupportConversationPrivacyService;
import com.gole.api.chat.application.SupportConversationPrivacyService.PurgeOutcome;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeCounts;
import com.gole.api.chat.application.port.out.SupportConversationPrivacyRepositoryPort.PurgeReceipt;
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

class AdminSupportPrivacyControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    private final SupportConversationPrivacyService privacy = mock(SupportConversationPrivacyService.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminSupportPrivacyController(privacy, audit))
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
    void adminPurgeRequiresExplicitHeadersAndProducesStructuredAudit() throws Exception {
        PurgeReceipt receipt = new PurgeReceipt(
                "receipt-1",
                "admin-1",
                "DATA_SUBJECT_REQUEST_FULFILLED",
                "key-hash",
                "fingerprint",
                NOW.minusSeconds(10),
                NOW,
                new PurgeCounts(2, 1, 1, 1, 1, 2, 0, 1));
        when(privacy.purge(
                        "room-1",
                        "admin-1",
                        "room-1",
                        SupportConversationPrivacyService.PurgeReasonCode.DATA_SUBJECT_REQUEST_FULFILLED,
                        true,
                        "550e8400-e29b-41d4-a716-446655440001"))
                .thenReturn(new PurgeOutcome(receipt, false));

        mvc.perform(
                        post("/api/admin/support-privacy/room-1/purge")
                                .header("Authorization", "Bearer admin-token")
                                .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "confirmation":"room-1",
                                  "reasonCode":"DATA_SUBJECT_REQUEST_FULFILLED",
                                  "preservationReviewed":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value("receipt-1"))
                .andExpect(jsonPath("$.roomId").doesNotExist())
                .andExpect(jsonPath("$.counts.messages").value(2))
                .andExpect(jsonPath("$.counts.assistantAnalyses").value(1))
                .andExpect(jsonPath("$.replayed").value(false));

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        assertThat(command.getValue().type()).isEqualTo(AdminActionType.SUPPORT_CONVERSATION_PURGE);
        assertThat(command.getValue().targetId()).isEqualTo("receipt-1");
        assertThat(command.getValue().reason())
                .contains("reasonCode=DATA_SUBJECT_REQUEST_FULFILLED", "messages=2", "analyses=1")
                .doesNotContain("room-1", "민감", "requester");
    }

    @Test
    void nonAdminNeverReachesPurgeService() throws Exception {
        mvc.perform(
                        post("/api/admin/support-privacy/room-1/purge")
                                .header("Authorization", "Bearer user-token")
                                .header("Idempotency-Key", "550e8400-e29b-41d4-a716-446655440001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                  "confirmation":"room-1",
                                  "reasonCode":"DATA_SUBJECT_REQUEST_FULFILLED",
                                  "preservationReviewed":true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));

        verify(privacy, never()).purge(any(), any(), any(), any(), anyBoolean(), any());
        verify(audit, never()).record(any());
    }
}
