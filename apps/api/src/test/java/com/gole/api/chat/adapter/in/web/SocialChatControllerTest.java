package com.gole.api.chat.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import com.gole.api.chat.application.ChatMessagingService;
import com.gole.api.chat.application.SocialChatService;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportStatus;
import com.gole.api.chat.domain.model.SupportTicket;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SocialChatControllerTest {

    private final SocialChatService chats = mock(SocialChatService.class);
    private final SupportTicketRepositoryPort tickets = mock(SupportTicketRepositoryPort.class);
    private final SocialChatController controller =
            new SocialChatController(chats, mock(ChatMessagingService.class), tickets);

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

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
        return request;
    }
}
