package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.gole.api.admin.domain.model.AdminTargetType;
import com.gole.api.chat.application.ChatMessagingService;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.SupportChatService;
import com.gole.api.chat.domain.model.ChatMessage;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminSupportControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-30T09:00:00Z");

    private final SupportChatService support = mock(SupportChatService.class);
    private final SocialChatService rooms = mock(SocialChatService.class);
    private final ChatMessagingService messaging = mock(ChatMessagingService.class);
    private final RecordAdminActionUseCase audit = mock(RecordAdminActionUseCase.class);
    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new AdminSupportController(support, rooms, messaging, audit))
            .addInterceptors(new AdminAuthInterceptor(sessions, new SessionCookie("false")))
            .setControllerAdvice(new GlobalExceptionHandler(mock(OperationalEventPublisher.class)))
            .build();

    @BeforeEach
    void sessions() {
        when(sessions.resolve("admin-token"))
                .thenReturn(Optional.of(new CurrentSession("admin-2", "admin2@gole.test", Role.ADMIN)));
        when(sessions.resolve("user-token"))
                .thenReturn(Optional.of(new CurrentSession("user-1", "user@gole.test", Role.USER)));
        when(sessions.resolve("")).thenReturn(Optional.empty());
    }

    @Test
    void adminCanTakeOverAssignedTicketAndReasonIsAudited() throws Exception {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "결제 문의", NOW).withSupportAgent(null, "admin-1");
        SupportTicket taken = ticket.transferTo("admin-2", NOW.plusSeconds(10));
        SocialChatRoom takenRoom = room.withSupportAgent("admin-1", "admin-2");
        when(support.takeOver("room-1", "admin-2", "기존 담당자 계정 정지"))
                .thenReturn(new SupportChatService.SupportTakeover(takenRoom, taken, "admin-1", "기존 담당자 계정 정지"));

        mvc.perform(post("/api/admin/support/room-1/takeover")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"기존 담당자 계정 정지\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value("room-1"))
                .andExpect(jsonPath("$.assigneeId").value("admin-2"))
                .andExpect(jsonPath("$.title").value("결제 문의"));

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        assertThat(command.getValue())
                .isEqualTo(new RecordAdminActionCommand(
                        "admin-2",
                        "admin2@gole.test",
                        AdminActionType.SUPPORT_TAKEOVER,
                        AdminTargetType.SUPPORT_TICKET,
                        "room-1",
                        "previousAssignee=admin-1; reason=기존 담당자 계정 정지"));
    }

    @Test
    void privacyInboxExposesCategoryAndResponseTarget() throws Exception {
        SupportTicket ticket =
                SupportTicket.opened("room-privacy", "user-1", SupportCategory.PRIVACY_CORRECTION_DELETION, NOW);
        SocialChatRoom room = SocialChatRoom.support("room-privacy", "user-1", "삭제 요청", NOW);
        when(support.inbox("admin-2", null, SupportCategory.PRIVACY_CORRECTION_DELETION, 50))
                .thenReturn(List.of(ticket));
        when(rooms.requireRoom("room-privacy")).thenReturn(room);

        mvc.perform(get("/api/admin/support")
                        .header("Authorization", "Bearer admin-token")
                        .param("category", "PRIVACY_CORRECTION_DELETION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("PRIVACY_CORRECTION_DELETION"))
                .andExpect(jsonPath("$[0].responseDueAt").value("2026-09-09T09:00:00Z"));
    }

    @Test
    void blankReasonIsRejectedBeforeTakeoverAndAudit() throws Exception {
        mvc.perform(post("/api/admin/support/room-1/takeover")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(support, never()).takeOver(any(), any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void rejectedTakeoverDoesNotProduceFalseAuditEvidence() throws Exception {
        when(support.takeOver("room-1", "admin-2", "완료된 문의"))
                .thenThrow(new BadRequestException("SUPPORT_ALREADY_RESOLVED", "완료된 문의입니다"));

        mvc.perform(post("/api/admin/support/room-1/takeover")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"완료된 문의\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUPPORT_ALREADY_RESOLVED"));

        verify(audit, never()).record(any());
    }

    @Test
    void regularUserCannotTakeOverTicket() throws Exception {
        mvc.perform(post("/api/admin/support/room-1/takeover")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"담당자 부재\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));

        verify(support, never()).takeOver(any(), any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void repeatedAssignmentReturnsCurrentTicketWithoutFalseAudit() throws Exception {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-2", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-2");
        when(support.assignToSelf("room-1", "admin-2"))
                .thenReturn(new SupportChatService.SupportConversation(room, ticket, false));

        mvc.perform(post("/api/admin/support/room-1/assign").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(audit, never()).record(any());
    }

    @Test
    void repeatedReopenReturnsCurrentTicketWithoutFalseAudit() throws Exception {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-2", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-2");
        when(support.reopen("room-1", "admin-2")).thenReturn(new SupportChatService.SupportTransition(ticket, false));
        when(rooms.requireRoom("room-1")).thenReturn(room);

        mvc.perform(post("/api/admin/support/room-1/reopen").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(audit, never()).record(any());
    }

    @Test
    void realResolveStateChangeProducesOneAuditRecord() throws Exception {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW)
                .assignTo("admin-2", NOW)
                .resolve(NOW.plusSeconds(10));
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-2");
        when(support.resolve("room-1", "admin-2")).thenReturn(new SupportChatService.SupportTransition(ticket, true));
        when(rooms.requireRoom("room-1")).thenReturn(room);

        mvc.perform(post("/api/admin/support/room-1/resolve").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        ArgumentCaptor<RecordAdminActionCommand> command = ArgumentCaptor.forClass(RecordAdminActionCommand.class);
        verify(audit).record(command.capture());
        assertThat(command.getValue().type()).isEqualTo(AdminActionType.SUPPORT_RESOLVE);
    }

    @Test
    void assignedAdminCanPageOlderSupportMessagesWithStableCursor() throws Exception {
        Instant before = NOW.plusSeconds(120);
        when(messaging.history("room-1", "admin-2", before, "message-2", 20))
                .thenReturn(List.of(new ChatMessage("message-1", "room-1", "user-1", "첫 문의", NOW.plusSeconds(30))));

        mvc.perform(get("/api/admin/support/room-1/messages")
                        .header("Authorization", "Bearer admin-token")
                        .param("beforeSentAt", before.toString())
                        .param("beforeId", "message-2")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("message-1"));

        verify(support).requireAssignedTo("room-1", "admin-2");
        verify(messaging).history("room-1", "admin-2", before, "message-2", 20);
    }
}
