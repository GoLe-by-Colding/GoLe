package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.gole.api.notification.domain.model.DevicePlatform;
import com.gole.api.notification.domain.model.DeviceToken;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 푸시 발송 경로. 실행기를 동기로 주입해 결정적으로 검증한다. (모바일 앱 스펙 R8.2~R8.4) */
class PushDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private InMemoryDeviceTokens tokens;
    private RecordingPushSender sender;
    private PushDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        tokens = new InMemoryDeviceTokens();
        sender = new RecordingPushSender();
        dispatcher = new PushDispatcher(tokens, sender, Runnable::run);
    }

    @Test
    @DisplayName("수신자의 모든 단말로 보낸다")
    void dispatch_sendsToEveryDeviceOfRecipient() {
        tokens.upsert(new DeviceToken("tok-phone", "u1", DevicePlatform.ANDROID, NOW));
        tokens.upsert(new DeviceToken("tok-tablet", "u1", DevicePlatform.IOS, NOW));
        tokens.upsert(new DeviceToken("tok-other", "u2", DevicePlatform.ANDROID, NOW));

        dispatcher.dispatch("u1", "GoLe", "주문이 접수되었습니다", "/orders/o1");

        assertThat(sender.sent).hasSize(2);
        assertThat(sender.sent).extracting(m -> m.token()).containsExactlyInAnyOrder("tok-phone", "tok-tablet");
        assertThat(sender.sent.getFirst().link()).isEqualTo("/orders/o1");
    }

    @Test
    @DisplayName("FCM이 거부한 토큰은 즉시 제거한다")
    void dispatch_removesTokenRejectedByFcm() {
        tokens.upsert(new DeviceToken("tok-dead", "u1", DevicePlatform.ANDROID, NOW));
        tokens.upsert(new DeviceToken("tok-live", "u1", DevicePlatform.ANDROID, NOW));
        sender.markInvalid("tok-dead");

        dispatcher.dispatch("u1", "GoLe", "메시지", null);

        assertThat(tokens.findByAccountId("u1"))
                .extracting(DeviceToken::getToken)
                .containsExactly("tok-live");
    }

    @Test
    @DisplayName("일시적 실패는 토큰을 지우지 않는다")
    void dispatch_keepsTokenOnTransientFailure() {
        tokens.upsert(new DeviceToken("tok-live", "u1", DevicePlatform.ANDROID, NOW));

        dispatcher.dispatch("u1", "GoLe", "메시지", null);

        assertThat(tokens.findByAccountId("u1")).hasSize(1);
    }

    @Test
    @DisplayName("어댑터가 예외를 던져도 호출자에게 전파하지 않는다")
    void dispatch_swallowsAdapterFailure() {
        tokens.upsert(new DeviceToken("tok", "u1", DevicePlatform.ANDROID, NOW));
        sender.throwOnSend(new IllegalStateException("FCM 폭발"));

        // 푸시는 주문·채팅 트랜잭션에 딸린 부수 효과다. 여기서 새어 나가면 거래가 실패한다. (R8.3)
        assertThatCode(() -> dispatcher.dispatch("u1", "GoLe", "메시지", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("등록된 단말이 없으면 아무것도 보내지 않는다")
    void dispatch_doesNothingWithoutDevices() {
        dispatcher.dispatch("u-without-devices", "GoLe", "메시지", null);

        assertThat(sender.sent).isEmpty();
    }
}
