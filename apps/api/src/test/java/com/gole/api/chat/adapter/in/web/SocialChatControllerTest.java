package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.account.application.service.ThirdPartyProvisionConsentService;
import com.gole.api.chat.application.ChatMessagingService;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SocialChatControllerTest {

    private final SocialChatService chats = mock(SocialChatService.class);
    private final ChatMessagingService messaging = mock(ChatMessagingService.class);
    private final SupportTicketRepositoryPort tickets = mock(SupportTicketRepositoryPort.class);
    private final ThirdPartyProvisionConsentService thirdPartyProvisionConsents =
            mock(ThirdPartyProvisionConsentService.class);
    private final SocialChatController controller =
            new SocialChatController(chats, messaging, tickets, thirdPartyProvisionConsents);

    @Test
    void myRoomsLoadsSupportStateInOneBatch() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SocialChatRoom support = SocialChatRoom.support("support-1", "account-1", "결제 문의", now);
        SocialChatRoom group =
                SocialChatRoom.group("group-1", "account-1", List.of("account-2", "account-3"), "모임", now);
        SupportTicket ticket =
                new SupportTicket("support-1", "account-1", SupportStatus.IN_PROGRESS, "admin-1", now, now, null);
        when(chats.mySocialRooms("account-1", 100)).thenReturn(List.of(support, group));
        when(tickets.findByRoomIds(List.of("support-1"))).thenReturn(List.of(ticket));

        var response = controller.myRooms(authenticated("account-1"));

        assertThat(response).hasSize(2);
        assertThat(response.getFirst().supportStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.get(1).supportStatus()).isNull();
        verify(tickets).findByRoomIds(List.of("support-1"));
    }

    @Test
    void creatingOrJoiningNonSupportRoomsRequiresCurrentConsentBeforeMutation() {
        when(chats.findExistingDirect("legacy-user", "peer")).thenReturn(java.util.Optional.empty());
        SocialChatRoom group =
                SocialChatRoom.group("room-1", "legacy-user", List.of("member-1", "member-2"), "group", Instant.now());
        when(chats.requireReadable("room-1", "legacy-user")).thenReturn(group);
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.REQUIRED_CODE, "consent required"))
                .when(thirdPartyProvisionConsents)
                .requireCurrent("legacy-user");

        assertThatThrownBy(() -> controller.createDirect(
                        new SocialChatController.CreateDirectRequest("peer"), authenticated("legacy-user")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.createGroup(
                        new SocialChatController.CreateGroupRequest("group", List.of("a", "b")),
                        authenticated("legacy-user")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.invite(
                        "room-1", new SocialChatController.InviteMemberRequest("peer"), authenticated("legacy-user")))
                .isInstanceOf(ForbiddenException.class);

        verify(chats, never()).createDirect("legacy-user", "peer");
        verify(chats, never()).createGroup("legacy-user", "group", List.of("a", "b"));
        verify(chats, never()).invite("room-1", "legacy-user", "peer");
    }

    @Test
    void existingDirectRoomCanBeReenteredAfterWithdrawal() {
        SocialChatRoom room = SocialChatRoom.direct("direct-1", "legacy-user", "peer", Instant.now());
        when(chats.findExistingDirect("legacy-user", "peer")).thenReturn(java.util.Optional.of(room));

        var response = controller.createDirect(
                new SocialChatController.CreateDirectRequest("peer"), authenticated("legacy-user"));

        assertThat(response.id()).isEqualTo("direct-1");
        verifyNoInteractions(thirdPartyProvisionConsents);
        verify(chats, never()).createDirect("legacy-user", "peer");
    }

    @Test
    void groupCreationAndInviteRequireEveryNewlyExposedSubjectsConsent() {
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE, "subject consent"))
                .when(thirdPartyProvisionConsents)
                .requireCurrentSubject("member-2");

        assertThatThrownBy(() -> controller.createGroup(
                        new SocialChatController.CreateGroupRequest("group", List.of("member-1", "member-2")),
                        authenticated("owner")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE);
        verify(chats, never()).createGroup("owner", "group", List.of("member-1", "member-2"));

        SocialChatRoom existing =
                SocialChatRoom.group("room-1", "owner", List.of("member-1", "member-2"), "group", Instant.now());
        when(chats.requireReadable("room-1", "owner")).thenReturn(existing);
        doThrow(new ForbiddenException(ThirdPartyProvisionConsentService.SUBJECT_REQUIRED_CODE, "subject consent"))
                .when(thirdPartyProvisionConsents)
                .requireCurrentSubject("member-1");

        assertThatThrownBy(() -> controller.invite(
                        "room-1", new SocialChatController.InviteMemberRequest("new-member"), authenticated("owner")))
                .isInstanceOf(ForbiddenException.class);
        verify(chats, never()).invite("room-1", "owner", "new-member");
    }

    @Test
    void supportCreationPersistsFirstMessageForAuthenticatedRequester() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        SocialChatRoom support = SocialChatRoom.support("support-1", "admin-1", "내 계정 문의", now);
        SupportTicket ticket = SupportTicket.opened("support-1", "admin-1", SupportCategory.PRIVACY_ACCESS, now);
        when(chats.createSupport("admin-1", "내 계정 문의", SupportCategory.PRIVACY_ACCESS))
                .thenReturn(new SocialChatService.SupportConversation(support, ticket));

        var response = controller.createSupport(
                new SocialChatController.CreateSupportRequest(
                        "내 계정 문의", "운영 기능을 확인하고 싶습니다", SupportCategory.PRIVACY_ACCESS),
                authenticated("admin-1"));

        assertThat(response.id()).isEqualTo("support-1");
        assertThat(response.supportCategory()).isEqualTo("PRIVACY_ACCESS");
        assertThat(response.progressDueAt()).isEqualTo("2026-09-02T00:00:00Z");
        assertThat(response.responseDueAt()).isEqualTo("2026-09-09T00:00:00Z");
        verify(messaging).sendSupportOpening("support-1", "admin-1", "운영 기능을 확인하고 싶습니다");
        verifyNoInteractions(thirdPartyProvisionConsents);
    }

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
        return request;
    }
}
