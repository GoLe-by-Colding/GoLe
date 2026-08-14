package com.gole.api.chat.adapter.out.pubsub;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gole.api.chat.domain.model.ChatMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 채팅 메시지 ↔ JSON. Redis Pub/Sub 페이로드와 SSE 이벤트 본문이 같은 형식을 쓴다.
 *
 * <p>ObjectMapper를 주입받지 않고 직접 구성한다. 이건 <b>외부로 나가는 통신 형식</b>이라
 * 애플리케이션 전역 Jackson 설정이 바뀐다고 브라우저와의 약속이 따라 바뀌면 안 된다.
 *
 * <p>손으로 JSON을 조립하지 않는다 — 이스케이프를 직접 다루다 따옴표가 든 메시지가
 * 전달되지 않는 버그가 있었다. 문자열 조합은 값에 구분자가 섞이는 순간 반드시 깨진다.
 */
@Component
public class ChatMessageCodec {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageCodec.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // 프론트가 new Date(sentAt)로 읽으므로 숫자 타임스탬프가 아니라 ISO 문자열이어야 한다.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 형식이 나중에 확장돼도 구버전 인스턴스가 죽지 않게 한다.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    public String encode(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            // 도메인 불변식을 통과한 값이라 여기 오면 설정 문제다. 조용히 넘기면 안 된다.
            throw new IllegalStateException("채팅 메시지 직렬화 실패: " + message.id(), e);
        }
    }

    /**
     * 페이로드를 메시지로 되돌린다. 깨졌으면 빈 값.
     *
     * <p>예외를 던지지 않는 이유: 이 결과는 SSE 리스너 안에서 쓰이는데, 거기서 예외가 나면
     * 그 방의 실시간 수신이 통째로 끊긴다. 메시지 하나를 잃는 편이 낫다.
     */
    public Optional<ChatMessage> decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, ChatMessage.class));
        } catch (Exception e) {
            log.warn("채팅 메시지 역직렬화 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
