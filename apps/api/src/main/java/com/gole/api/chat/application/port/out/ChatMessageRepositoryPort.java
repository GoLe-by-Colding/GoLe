package com.gole.api.chat.application.port.out;

import com.gole.api.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: 채팅 메시지 저장소. 커서는 {@code (sentAt, id)} 한 쌍이라 같은 시각 메시지도 순서가 흔들리지 않는다.
 */
public interface ChatMessageRepositoryPort {

    ChatMessage save(ChatMessage message);

    Optional<ChatMessage> findById(String messageId);

    /** 가장 최근 메시지부터 {@code limit} 건(최신 순). */
    List<ChatMessage> findLatest(String roomId, int limit);

    /** 커서보다 앞선 메시지를 커서에 가까운 것부터 {@code limit} 건(최신 순). */
    List<ChatMessage> findBefore(String roomId, Instant sentAt, String messageId, int limit);

    /** 커서보다 뒤의 메시지를 커서에 가까운 것부터 {@code limit} 건(오래된 순). */
    List<ChatMessage> findAfter(String roomId, Instant sentAt, String messageId, int limit);
}
