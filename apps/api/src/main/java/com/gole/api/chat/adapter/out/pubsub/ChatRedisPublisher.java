package com.gole.api.chat.adapter.out.pubsub;

import com.gole.api.chat.domain.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub으로 채팅 메시지를 브로드캐스트한다. 채널: {@code chat:<roomId>}.
 */
@Component
public class ChatRedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatRedisPublisher.class);
    private static final String CHANNEL_PREFIX = "chat:";

    private final StringRedisTemplate redisTemplate;

    public ChatRedisPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(ChatMessage message) {
        // ObjectMapper 없이 단순 JSON 문자열 조합 (값 이스케이프 적용)
        String payload = "{"
                + "\"id\":\"" + esc(message.id()) + "\","
                + "\"senderId\":\"" + esc(message.senderId()) + "\","
                + "\"content\":\"" + esc(message.content()) + "\","
                + "\"sentAt\":\"" + esc(message.sentAt().toString()) + "\""
                + "}";
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + message.roomId(), payload);
        } catch (Exception e) {
            log.warn("Chat publish failed: {}", e.getMessage());
        }
    }

    /** 큰따옴표·역슬래시만 이스케이프(채팅 메시지용 최소 이스케이프). */
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
