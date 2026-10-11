package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.in.SocialChatUseCase;
import com.gole.api.chat.application.port.in.StartSupportConversationUseCase;
import com.gole.api.chat.application.port.in.StartSupportConversationUseCase.SupportConversation;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.web.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 소셜 채팅 REST 경계. 동의 관문은 SocialChatConsentTest, 문의 시작 트랜잭션은 SupportIntakeServiceTest 가 본다. */
class SocialChatControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private final SocialChatUseCase chats = mock(SocialChatUseCase.class);
    private final StartSupportConversationUseCase supportIntake = mock(StartSupportConversationUseCase.class);
    private final SocialChatController controller = new SocialChatController(chats, supportIntake);

    @Test
    @DisplayName("내 방 목록의 문의 상태는 한 번에 읽는다")
    void myRoomsLoadsSupportStateInOneBatch() {
        SocialChatRoom support = SocialChatRoom.support("support-1", "account-1", "결제 문의", NOW);
        SocialChatRoom group =
                SocialChatRoom.group("group-1", "account-1", List.of("account-2", "account-3"), "모임", NOW);
        SupportTicket ticket =
                new SupportTicket("support-1", "account-1", SupportStatus.IN_PROGRESS, "admin-1", NOW, NOW, null);
        when(chats.mySocialRooms("account-1", 100)).thenReturn(List.of(support, group));
        when(chats.supportTicketsOf(List.of("support-1"))).thenReturn(List.of(ticket));

        var response = controller.myRooms(authenticated("account-1"));

        assertThat(response).hasSize(2);
        assertThat(response.getFirst().supportStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.get(1).supportStatus()).isNull();
        verify(chats).supportTicketsOf(List.of("support-1"));
    }

    @Test
    @DisplayName("1:1·그룹·초대는 세션 계정으로 유스케이스에 넘긴다")
    void directGroupInvite_delegateWithAuthenticatedAccount() {
        SocialChatRoom direct = SocialChatRoom.direct("direct-1", "me", "peer", NOW);
        SocialChatRoom group = SocialChatRoom.group("group-1", "me", List.of("a", "b"), "모임", NOW);
        when(chats.startDirect("me", "peer")).thenReturn(direct);
        when(chats.startGroup("me", "모임", List.of("a", "b"))).thenReturn(group);
        when(chats.inviteMember("group-1", "me", "c")).thenReturn(group);
        when(chats.supportTicketOf(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        assertThat(controller
                        .createDirect(new SocialChatController.CreateDirectRequest("peer"), authenticated("me"))
                        .id())
                .isEqualTo("direct-1");
        assertThat(controller
                        .createGroup(
                                new SocialChatController.CreateGroupRequest("모임", List.of("a", "b")),
                                authenticated("me"))
                        .id())
                .isEqualTo("group-1");
        assertThat(controller
                        .invite("group-1", new SocialChatController.InviteMemberRequest("c"), authenticated("me"))
                        .id())
                .isEqualTo("group-1");
    }

    @Test
    @DisplayName("문의 시작은 제목·분류·첫 메시지를 함께 넘기고 문의 기한을 내린다")
    void supportCreation_delegatesFirstMessage() {
        SocialChatRoom support = SocialChatRoom.support("support-1", "admin-1", "내 계정 문의", NOW);
        SupportTicket ticket = SupportTicket.opened("support-1", "admin-1", SupportCategory.PRIVACY_ACCESS, NOW);
        when(supportIntake.start("admin-1", "내 계정 문의", SupportCategory.PRIVACY_ACCESS, "운영 기능을 확인하고 싶습니다"))
                .thenReturn(new SupportConversation(support, ticket));

        var response = controller.createSupport(
                new SocialChatController.CreateSupportRequest(
                        "내 계정 문의", "운영 기능을 확인하고 싶습니다", SupportCategory.PRIVACY_ACCESS),
                authenticated("admin-1"));

        assertThat(response.id()).isEqualTo("support-1");
        assertThat(response.supportCategory()).isEqualTo("PRIVACY_ACCESS");
        assertThat(response.progressDueAt()).isEqualTo("2026-09-02T00:00:00Z");
        assertThat(response.responseDueAt()).isEqualTo("2026-09-09T00:00:00Z");
    }

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthenticatedUser.ATTRIBUTE, accountId);
        return request;
    }
}
