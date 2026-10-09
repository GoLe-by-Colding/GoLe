package com.gole.api.chat.application.service;

import com.gole.api.chat.application.port.in.PostChatMessageUseCase;
import com.gole.api.chat.domain.model.ChatMessage;
import org.springframework.stereotype.Service;

/** {@link PostChatMessageUseCase}를 기존 사용자 메시지 전송 경로({@link ChatMessagingService#send})에 위임한다. */
@Service
public class PostChatMessageService implements PostChatMessageUseCase {

    private final ChatMessagingService messaging;

    public PostChatMessageService(ChatMessagingService messaging) {
        this.messaging = messaging;
    }

    @Override
    public ChatMessage post(String roomId, String actorId, String content) {
        return messaging.send(roomId, actorId, content);
    }
}
