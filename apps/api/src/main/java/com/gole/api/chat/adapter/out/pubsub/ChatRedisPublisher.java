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
    private final ChatMessageCodec codec;

    public ChatRedisPublisher(StringRedisTemplate redisTemplate, ChatMessageCodec codec) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
    }

    public void publish(ChatMessage message) {
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + message.roomId(), codec.encode(message));
        } catch (Exception e) {
            log.warn("Chat publish failed: {}", e.getMessage());
        }
    }
}
