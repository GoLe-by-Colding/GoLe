package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.SocialChatRoom;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;

/** Inbound port: 운영팀 문의 시작. 문의방·티켓과 첫 메시지를 한 트랜잭션으로 저장한다. */
public interface StartSupportConversationUseCase {

    SupportConversation start(String actorId, String title, SupportCategory category, String firstMessage);

    record SupportConversation(SocialChatRoom room, SupportTicket ticket) {}
}
