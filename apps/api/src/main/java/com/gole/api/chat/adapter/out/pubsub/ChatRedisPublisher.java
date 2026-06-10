package com.gole.api.chat.adapter.out.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gole.api.chat.domain.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub으로 채팅 메시지를 브로드캐스트한다.
 * 채널: {@code chat:<roomId>}.
 * 다중 인스턴스에서도 모든 SSE 구독자에게 전달된다.
 */
@Component
public class ChatRedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatRedisPublisher.class);
    private static final String CHANNEL_PREFIX = "chat:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatRedisPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(ChatMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new MessagePayload(message.id(), message.senderId(), message.content(),
                            message.sentAt().toString()));
            redisTemplate.convertAndSend(CHANNEL_PREFIX + message.roomId(), payload);
        } catch (JsonProcessingException e) {
            log.warn("Chat publish failed: {}", e.getMessage());
        }
    }

    public record MessagePayload(String id, String senderId, String content, String sentAt) {}
}
