package com.gole.api.chat.adapter.out.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.domain.model.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 채팅 메시지 직렬화(Redis Pub/Sub · SSE 페이로드).
 *
 * <p>회귀: 손으로 만든 JSON과 정규식 파서를 쓰던 시절, 따옴표가 든 메시지가 실시간으로
 * 전달되지 않았다. 발행 쪽은 {@code "}를 {@code \"}로 이스케이프하는데 수신 쪽 정규식
 * {@code "([^"]*)"}이 첫 따옴표에서 끊겨 값이 잘렸고, 그 잘린 값을 다시 이스케이프 없이
 * 이어붙여 깨진 JSON을 SSE로 내보냈다. 프론트는 파싱 실패를 조용히 무시하므로
 * <b>메시지가 그냥 사라진 것처럼</b> 보였다(저장은 되어 새로고침하면 나타난다).
 */
class ChatMessageCodecTest {

    private final ChatMessageCodec codec = new ChatMessageCodec();

    private static ChatMessage messageWith(String content) {
        return new ChatMessage("m-1", "room-1", "user-1", content, Instant.parse("2026-08-14T05:00:00Z"));
    }

    /** 사용자가 실제로 칠 법한 문자들. 하나라도 깨지면 그 메시지는 상대에게 도착하지 않는다. */
    @Test
    void roundTrip_preservesTrickyContent() {
        List<String> contents = List.of(
                "안녕하세요",
                "안녕 \"레고\" 팔아요",
                "역슬래시 \\ 포함",
                "줄바꿈\n두 줄",
                "탭\t포함",
                "이모지 🧱🐳",
                "중괄호 {\"fake\":\"json\"}",
                "따옴표만 \"");

        for (String content : contents) {
            Optional<ChatMessage> decoded = codec.decode(codec.encode(messageWith(content)));

            assertThat(decoded).describedAs("content=%s", content).isPresent();
            assertThat(decoded.get().content()).isEqualTo(content);
        }
    }

    @Test
    void roundTrip_preservesAllFields() {
        ChatMessage original = messageWith("본문");

        ChatMessage decoded = codec.decode(codec.encode(original)).orElseThrow();

        assertThat(decoded.id()).isEqualTo("m-1");
        assertThat(decoded.roomId()).isEqualTo("room-1");
        assertThat(decoded.senderId()).isEqualTo("user-1");
        assertThat(decoded.sentAt()).isEqualTo(Instant.parse("2026-08-14T05:00:00Z"));
    }

    /** 시각은 프론트가 Date로 읽으므로 숫자 타임스탬프가 아니라 ISO 문자열이어야 한다. */
    @Test
    void encode_writesInstantAsIsoString() {
        assertThat(codec.encode(messageWith("본문"))).contains("\"sentAt\":\"2026-08-14T05:00:00Z\"");
    }

    /**
     * 깨진 페이로드는 예외 대신 빈 값이어야 한다. Redis 채널에 다른 형식이 섞여 들어와도
     * SSE 리스너가 죽으면 그 방의 실시간 수신이 통째로 끊긴다.
     */
    @Test
    void decode_returnsEmpty_onMalformedPayload() {
        assertThat(codec.decode("not json")).isEmpty();
        assertThat(codec.decode("{\"id\":\"m-1\"}")).isEmpty(); // 필수 필드 누락
        assertThat(codec.decode("")).isEmpty();
        assertThat(codec.decode(null)).isEmpty();
    }
}
