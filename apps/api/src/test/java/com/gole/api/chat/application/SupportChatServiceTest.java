package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportInternalNotePort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final SocialChatRoomRepositoryPort rooms = mock(SocialChatRoomRepositoryPort.class);
    private final SupportTicketRepositoryPort tickets = mock(SupportTicketRepositoryPort.class);
    private final SupportInternalNotePort notes = mock(SupportInternalNotePort.class);
    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final SupportChatService service =
            new SupportChatService(rooms, tickets, notes, accounts, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void admins() {
        when(accounts.findById("admin-1")).thenReturn(Optional.of(admin("admin-1")));
        when(accounts.findById("admin-2")).thenReturn(Optional.of(admin("admin-2")));
    }

    @Test
    void countUnassignedUsesRepositoryCountWithoutReadingPrivateContent() {
        when(tickets.countByStatus(SupportStatus.UNASSIGNED)).thenReturn(3L);

        assertThat(service.countUnassigned()).isEqualTo(3);

        verify(tickets).countByStatus(SupportStatus.UNASSIGNED);
        verify(tickets, never()).findByStatus(any(), anyInt());
    }

    @Test
    void concurrentAssignmentConflictDoesNotWriteRoom() {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW);
        SocialChatRoom room = SocialChatRoom.support("room-1", "user-1", "문의", NOW);
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(ticket));
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        when(tickets.save(any())).thenThrow(new ConflictException("SUPPORT_CONCURRENT_UPDATE", "conflict"));

        assertThatThrownBy(() -> service.assignToSelf("room-1", "admin-1")).isInstanceOf(ConflictException.class);

        verify(rooms, never()).save(any());
    }

    @Test
    void transferWritesAuthoritativeTicketBeforeRoomMembership() {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-1");
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(ticket));
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rooms.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.transfer("room-1", "admin-1", "admin-2");

        var order = org.mockito.Mockito.inOrder(tickets, rooms);
        order.verify(tickets).save(any());
        order.verify(rooms).save(any());
    }

    @Test
    void repeatedAssignmentDoesNotWriteOrReportAChange() {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-1");
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(ticket));
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));

        SupportChatService.SupportConversation result = service.assignToSelf("room-1", "admin-1");

        assertThat(result.changed()).isFalse();
        assertThat(result.ticket()).isSameAs(ticket);
        verify(tickets, never()).save(any());
        verify(rooms, never()).save(any());
    }

    @Test
    void transferToCurrentAssigneeIsRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.transfer("room-1", "admin-1", "admin-1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 내가");

        verify(tickets, never()).findByRoomId(any());
        verify(tickets, never()).save(any());
        verify(rooms, never()).save(any());
    }

    @Test
    void repeatedResolveAndReopenDoNotBumpVersion() {
        SupportTicket assigned = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SupportTicket resolved = assigned.resolve(NOW);
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(resolved), Optional.of(assigned));

        SupportChatService.SupportTransition resolveResult = service.resolve("room-1", "admin-1");
        SupportChatService.SupportTransition reopenResult = service.reopen("room-1", "admin-1");

        assertThat(resolveResult.changed()).isFalse();
        assertThat(resolveResult.ticket()).isSameAs(resolved);
        assertThat(reopenResult.changed()).isFalse();
        assertThat(reopenResult.ticket()).isSameAs(assigned);
        verify(tickets, never()).save(any());
    }

    @Test
    void resolvePersistsAndReportsARealStateChange() {
        SupportTicket assigned = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(assigned));
        when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SupportChatService.SupportTransition result = service.resolve("room-1", "admin-1");

        assertThat(result.changed()).isTrue();
        assertThat(result.ticket().status()).isEqualTo(SupportStatus.RESOLVED);
        verify(tickets).save(any());
    }

    @Test
    void takeoverReplacesPreviousAssigneeAndRoomMembership() {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-1");
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(ticket));
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rooms.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SupportChatService.SupportTakeover result = service.takeOver("room-1", "admin-2", "  기존 담당자 계정 정지  ");

        assertThat(result.previousAssigneeId()).isEqualTo("admin-1");
        assertThat(result.reason()).isEqualTo("기존 담당자 계정 정지");
        assertThat(result.ticket().assigneeId()).isEqualTo("admin-2");
        assertThat(result.ticket().status()).isEqualTo(SupportStatus.IN_PROGRESS);
        assertThat(result.room().memberIds()).containsExactly("user-1", "admin-2");

        var order = org.mockito.Mockito.inOrder(tickets, rooms);
        order.verify(tickets).save(any());
        order.verify(rooms).save(any());
    }

    @Test
    void concurrentTakeoverConflictDoesNotChangeRoomMembership() {
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-1");
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(ticket));
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        when(tickets.save(any())).thenThrow(new ConflictException("SUPPORT_CONCURRENT_UPDATE", "conflict"));

        assertThatThrownBy(() -> service.takeOver("room-1", "admin-2", "기존 담당자 부재"))
                .isInstanceOf(ConflictException.class);

        verify(rooms, never()).save(any());
    }

    @Test
    void takeoverRequiresReasonBeforeReadingPrivateTicket() {
        assertThatThrownBy(() -> service.takeOver("room-1", "admin-2", "  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("사유");

        verify(tickets, never()).findByRoomId(any());
        verify(tickets, never()).save(any());
        verify(rooms, never()).save(any());
    }

    @Test
    void takeoverRejectsOversizedReasonBeforeReadingPrivateTicket() {
        assertThatThrownBy(() -> service.takeOver("room-1", "admin-2", "사".repeat(501)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("500자");

        verify(tickets, never()).findByRoomId(any());
        verify(tickets, never()).save(any());
    }

    @Test
    void takeoverRejectsUnassignedResolvedAndOwnTicketsWithoutWriting() {
        SupportTicket unassigned = SupportTicket.opened("room-1", "user-1", NOW);
        SupportTicket owned = unassigned.assignTo("admin-2", NOW);
        SupportTicket resolved = unassigned.assignTo("admin-1", NOW).resolve(NOW);

        when(tickets.findByRoomId("room-1"))
                .thenReturn(Optional.of(unassigned), Optional.of(owned), Optional.of(resolved));

        assertThatThrownBy(() -> service.takeOver("room-1", "admin-2", "오배정"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("미배정");
        assertThatThrownBy(() -> service.takeOver("room-1", "admin-2", "중복"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이미 내가");
        assertThatThrownBy(() -> service.takeOver("room-1", "admin-2", "완료 상태 인수 시도"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("완료");

        verify(tickets, never()).save(any());
        verify(rooms, never()).save(any());
    }

    @Test
    void suspendedAdminCannotTakeOverTicket() {
        Account suspended = admin("admin-suspended");
        suspended.suspend("퇴사");
        when(accounts.findById("admin-suspended")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.takeOver("room-1", "admin-suspended", "기존 담당자 부재"))
                .isInstanceOf(ForbiddenException.class);

        verify(tickets, never()).findByRoomId(any());
        verify(tickets, never()).save(any());
    }

    private static Account admin(String id) {
        return Account.provisioned(id, new Email(id + "@gole.test"), new PasswordHash("hash"), Role.ADMIN);
    }
}
