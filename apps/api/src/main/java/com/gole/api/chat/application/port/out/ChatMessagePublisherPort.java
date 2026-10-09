package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.ChatMessage;

/** Outbound port: 저장된 메시지를 실시간 구독자(SSE)에게 브로드캐스트한다. 구현은 실패를 흡수한다 — 이력에서 재생된다. */
public interface ChatMessagePublisherPort {

    void publish(ChatMessage message);
}
