package com.gole.api.chat.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.in.StartSupportConversationUseCase.SupportConversation;
import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;
import com.gole.api.common.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class SupportIntakeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    private final SocialChatService socialChats = mock(SocialChatService.class);
    private final ChatMessagingService messaging = mock(ChatMessagingService.class);
    private final SupportIntakeService service = new SupportIntakeService(socialChats, messaging);

    @Test
    @DisplayName("문의방·티켓을 만들고 첫 메시지를 문의자로 저장한다")
    void start_createsRoomAndFirstMessage() {
        SocialChatRoom support = SocialChatRoom.support("support-1", "user-1", "결제 문의", NOW);
        SupportTicket ticket = SupportTicket.opened("support-1", "user-1", SupportCategory.GENERAL, NOW);
        when(socialChats.createSupport("user-1", "결제 문의", SupportCategory.GENERAL))
                .thenReturn(new SupportConversation(support, ticket));

        SupportConversation conversation = service.start("user-1", "결제 문의", SupportCategory.GENERAL, "환불이 안 돼요");

        assertThat(conversation.room()).isSameAs(support);
        verify(messaging).sendSupportOpening("support-1", "user-1", "환불이 안 돼요");
    }

    @Test
    @DisplayName("문의방을 만들지 못하면 첫 메시지를 보내지 않는다")
    void start_doesNotSendWhenRoomCreationFails() {
        when(socialChats.createSupport("user-1", "", SupportCategory.GENERAL))
                .thenThrow(new BadRequestException("SUPPORT_TITLE_REQUIRED", "제목"));

        assertThatThrownBy(() -> service.start("user-1", "", SupportCategory.GENERAL, "본문"))
                .isInstanceOf(BadRequestException.class);
        verify(messaging, never()).sendSupportOpening(any(), any(), any());
    }

    @Test
    @DisplayName("방 생성과 첫 메시지는 한 트랜잭션이다")
    void start_isTransactional() throws NoSuchMethodException {
        assertThat(SupportIntakeService.class
                        .getMethod("start", String.class, String.class, SupportCategory.class, String.class)
                        .isAnnotationPresent(Transactional.class))
                .isTrue();
    }
}
