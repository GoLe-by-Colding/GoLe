package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.application.port.in.RegisterDeviceTokenUseCase.RegisterDeviceTokenCommand;
import com.gole.api.notification.domain.model.DevicePlatform;
import com.gole.api.notification.domain.model.DeviceToken;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 단말 토큰 등록·해제. (모바일 앱 스펙 R8.1) */
class DeviceTokenServiceTest {

    private InMemoryDeviceTokens tokens;
    private DeviceTokenService service;

    @BeforeEach
    void setUp() {
        tokens = new InMemoryDeviceTokens();
        service = new DeviceTokenService(tokens, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("같은 토큰을 다시 등록해도 행이 늘지 않는다")
    void register_isIdempotentForSameToken() {
        RegisterDeviceTokenCommand command = new RegisterDeviceTokenCommand("u1", "tok", DevicePlatform.ANDROID);

        service.register(command);
        service.register(command);

        assertThat(tokens.findByAccountId("u1")).hasSize(1);
    }

    @Test
    @DisplayName("기기를 넘겨받아 다른 계정이 등록하면 소유가 옮겨간다")
    void register_movesTokenToNewOwner() {
        service.register(new RegisterDeviceTokenCommand("u1", "tok", DevicePlatform.ANDROID));

        service.register(new RegisterDeviceTokenCommand("u2", "tok", DevicePlatform.ANDROID));

        // 이전 소유자에게 남아 있으면 기기를 넘긴 사람의 알림이 새 사용자에게 간다.
        assertThat(tokens.findByAccountId("u1")).isEmpty();
        assertThat(tokens.findByAccountId("u2"))
                .extracting(DeviceToken::getToken)
                .containsExactly("tok");
    }

    @Test
    @DisplayName("해제는 토큰만으로 지운다")
    void unregister_deletesByTokenAlone() {
        service.register(new RegisterDeviceTokenCommand("u1", "tok", DevicePlatform.IOS));

        service.unregister("tok");

        assertThat(tokens.findByAccountId("u1")).isEmpty();
    }
}
