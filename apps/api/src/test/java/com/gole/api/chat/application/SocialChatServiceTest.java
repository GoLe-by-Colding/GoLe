package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.chat.application.port.out.ChatBlockRepositoryPort;
import com.gole.api.chat.application.port.out.SocialChatRoomRepositoryPort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SocialChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final SocialChatRoomRepositoryPort rooms = mock(SocialChatRoomRepositoryPort.class);
    private final ChatBlockRepositoryPort blocks = mock(ChatBlockRepositoryPort.class);
    private final SupportTicketRepositoryPort tickets = mock(SupportTicketRepositoryPort.class);
    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final SocialChatService service =
            new SocialChatService(rooms, blocks, tickets, accounts, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void accounts() {
        when(accounts.findById("user-1")).thenReturn(Optional.of(account("user-1", Role.USER)));
        when(accounts.findById("user-2")).thenReturn(Optional.of(account("user-2", Role.USER)));
        when(accounts.findById("user-3")).thenReturn(Optional.of(account("user-3", Role.USER)));
        when(accounts.findById("user-4")).thenReturn(Optional.of(account("user-4", Role.USER)));
        when(accounts.findById("admin-1")).thenReturn(Optional.of(account("admin-1", Role.ADMIN)));
        when(accounts.findById("admin-2")).thenReturn(Optional.of(account("admin-2", Role.ADMIN)));
    }

    @Test
    void mySocialRoomsFiltersInStorageBeforeLimit() {
        SocialChatRoom dm = SocialChatRoom.direct("room-1", "user-1", "user-2", NOW);
        when(rooms.findSocialByMember("user-1", 100)).thenReturn(List.of(dm));

        assertThat(service.mySocialRooms("user-1", 100)).containsExactly(dm);

        verify(rooms).findSocialByMember("user-1", 100);
        verify(rooms, never()).findByMember("user-1", 100);
    }

    @Test
    void adminCannotBeAddedToPrivateDirectRoom() {
        assertThatThrownBy(() -> service.createDirect("user-1", "admin-1")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void supportReadUsesCurrentTicketAssigneeNotStaleRoomMembers() {
        SocialChatRoom stale = SocialChatRoom.support("room-1", "user-1", "문의", NOW)
                .withSupportAgent(null, "admin-1")
                .withSupportAgent("admin-1", "admin-2")
                .withSupportAgent(null, "admin-1");
        when(rooms.findById("room-1")).thenReturn(Optional.of(stale));
        when(tickets.findByRoomId("room-1"))
                .thenReturn(Optional.of(
                        SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-2", NOW)));

        assertThatThrownBy(() -> service.requireReadable("room-1", "admin-1")).isInstanceOf(ForbiddenException.class);
        assertThat(service.requireReadable("room-1", "admin-2")).isSameAs(stale);
    }

    @Test
    void genericMessagePathRejectsAdminEvenWhenAssigned() {
        SocialChatRoom room =
                SocialChatRoom.support("room-1", "user-1", "문의", NOW).withSupportAgent(null, "admin-1");
        SupportTicket ticket = SupportTicket.opened("room-1", "user-1", NOW).assignTo("admin-1", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));
        when(tickets.findByRoomId("room-1")).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.requireSendable("room-1", "admin-1")).isInstanceOf(ForbiddenException.class);
        assertThat(service.requireAdminSupportSendable("room-1", "admin-1")).isSameAs(room);
    }

    @Test
    void supportRoomCannotUseGenericLeave() {
        SocialChatRoom room = SocialChatRoom.support("room-1", "user-1", "문의", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.leave("room-1", "user-1")).isInstanceOf(BadRequestException.class);
        verify(rooms, never()).save(room);
    }

    @Test
    void groupCreationRejectsBlockedPairAmongInvitees() {
        List<String> participants = List.of("user-1", "user-2", "user-3");
        when(blocks.blockedBetweenAny(participants, participants)).thenReturn(true);

        assertThatThrownBy(() -> service.createGroup("user-1", "테크닉 모임", List.of("user-2", "user-3")))
                .isInstanceOf(ForbiddenException.class);

        verify(blocks).blockedBetweenAny(participants, participants);
        verify(blocks, never()).blockedBetween(anyString(), anyString());
        verify(rooms, never()).save(any());
    }

    @Test
    void groupInviteRejectsBlockedCandidateWithOneBatchLookup() {
        SocialChatRoom group = SocialChatRoom.group("room-1", "user-1", List.of("user-2", "user-3"), "모임", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(group));
        when(blocks.blockedBetweenAny(List.of("user-4"), group.memberIds())).thenReturn(true);

        assertThatThrownBy(() -> service.invite("room-1", "user-1", "user-4")).isInstanceOf(ForbiddenException.class);

        verify(blocks).blockedBetweenAny(List.of("user-4"), group.memberIds());
        verify(blocks, never()).blockedBetween(anyString(), anyString());
        verify(rooms, never()).save(any());
    }

    @Test
    void existingGroupDoesNotFreezeWhenTwoMembersBlockEachOther() {
        SocialChatRoom group = SocialChatRoom.group("room-1", "user-1", List.of("user-2", "user-3"), "모임", NOW)
                .leave("user-3", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(group));
        when(blocks.blockedBetween("user-1", "user-2")).thenReturn(true);

        assertThat(service.requireSendable("room-1", "user-1")).isSameAs(group);

        verify(blocks, never()).blockedBetween("user-1", "user-2");
    }

    @Test
    void listingConversationUsesSameBidirectionalBlockPolicyAsDirectMessage() {
        SocialChatRoom listing = SocialChatRoom.listing("room-1", "listing-1", "user-1", "user-2", NOW);
        when(rooms.findById("room-1")).thenReturn(Optional.of(listing));
        when(blocks.blockedBetween("user-1", "user-2")).thenReturn(true);
        when(blocks.blockedBetween("user-2", "user-1")).thenReturn(true);

        assertThatThrownBy(() -> service.requireSendable("room-1", "user-1")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.requireCanStartPrivateConversation("user-2", "user-1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userCannotBlockSelf() {
        assertThatThrownBy(() -> service.block("user-1", "user-1", null)).isInstanceOf(BadRequestException.class);
        verify(blocks, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void myBlocksReturnsOnlyTargetsBlockedByActor() {
        when(blocks.blockedTargets("user-1")).thenReturn(List.of("user-2", "user-3"));

        assertThat(service.myBlockedAccountIds("user-1")).containsExactly("user-2", "user-3");

        verify(blocks).blockedTargets("user-1");
    }

    private static Account account(String id, Role role) {
        return Account.provisioned(id, new Email(id + "@gole.test"), new PasswordHash("hash"), role);
    }
}
