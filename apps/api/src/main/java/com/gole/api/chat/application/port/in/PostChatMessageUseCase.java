package com.gole.api.chat.application.port.in;

import com.gole.api.chat.domain.model.ChatMessage;

/**
 * Inbound port: 다른 컨텍스트가 사용자 명의로 방에 메시지를 남긴다. 사용자가 직접 보내는 메시지와 같은
 * 경로(전송 검사·저장·읽음·실시간 발행)를 탄다.
 */
public interface PostChatMessageUseCase {

    ChatMessage post(String roomId, String actorId, String content);
}
