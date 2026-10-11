package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.StartSupportConversationUseCase;
import com.gole.api.chat.domain.model.SupportCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영팀 문의 시작. 문의방·티켓을 만들고 첫 메시지를 같은 트랜잭션으로 저장한다 — 첫 메시지 없는 빈 문의가 남지 않는다. */
@Service
public class SupportIntakeService implements StartSupportConversationUseCase {

    private final SocialChatService socialChats;
    private final ChatMessagingService messaging;

    public SupportIntakeService(SocialChatService socialChats, ChatMessagingService messaging) {
        this.socialChats = socialChats;
        this.messaging = messaging;
    }

    @Override
    @Transactional
    public SupportConversation start(String actorId, String title, SupportCategory category, String firstMessage) {
        SupportConversation conversation = socialChats.createSupport(actorId, title, category);
        messaging.sendSupportOpening(conversation.room().id(), actorId, firstMessage);
        return conversation;
    }
}
