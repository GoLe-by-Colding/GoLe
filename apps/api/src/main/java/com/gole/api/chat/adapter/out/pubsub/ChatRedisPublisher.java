package com.gole.api.chat.adapter.out.pubsub;

import com.gole.api.chat.domain.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis Pub/Sub으로 채팅 메시지를 브로드캐스트한다. 채널: {@code chat:<roomId>}.
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
            String payload = objectMapper.writeValueAsString(new ChatPayload(
                    message.id(),
                    message.senderId(),
                    message.content(),
                    message.sentAt().toString()));
            redisTemplate.convertAndSend(CHANNEL_PREFIX + message.roomId(), payload);
        } catch (RuntimeException e) {
            log.warn("Chat publish failed: {}", e.getMessage());
        }
    }

    private record ChatPayload(String id, String senderId, String content, String sentAt) {}
}
