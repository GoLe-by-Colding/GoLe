package com.gole.api.chat.adapter.out.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gole.api.chat.domain.model.ChatMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class ChatRedisPublisherTest {

    @Test
    void publish_serializesQuotesBackslashesAndNewlinesAsValidJson() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ChatRedisPublisher publisher = new ChatRedisPublisher(redis, new ObjectMapper());
        String content = "따옴표 \"와 역슬래시 \\\n그리고 줄바꿈";

        publisher.publish(
                new ChatMessage("message-1", "room-1", "sender-1", content, Instant.parse("2026-08-09T00:00:00Z")));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(org.mockito.ArgumentMatchers.eq("chat:room-1"), payload.capture());
        @SuppressWarnings("unchecked")
        var decoded = new ObjectMapper().readValue(payload.getValue(), java.util.Map.class);
        assertThat(decoded.get("content")).isEqualTo(content);
        assertThat(decoded.get("senderId")).isEqualTo("sender-1");
    }
}
