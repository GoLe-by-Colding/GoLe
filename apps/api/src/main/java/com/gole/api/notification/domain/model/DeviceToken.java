package com.gole.api.notification.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 푸시 발송 대상 단말. 프레임워크 무의존. (모바일 앱 스펙 R8.1)
 *
 * <p><b>토큰 자체가 식별자다.</b> FCM 등록 토큰은 단말·앱 설치마다 유일하므로 별도 id를 두면
 * 같은 단말이 여러 행으로 쌓인다. 재등록이 그대로 덮어쓰기가 되는 것도 이 선택 덕분이다 —
 * 기기를 넘겨받아 다른 계정이 로그인하면 accountId만 바뀐다.
 */
public final class DeviceToken {

    private final String token;
    private final String accountId;
    private final DevicePlatform platform;
    private final Instant registeredAt;

    public DeviceToken(String token, String accountId, DevicePlatform platform, Instant registeredAt) {
        this.token = requireText(token, "token");
        this.accountId = requireText(accountId, "accountId");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    }

    public String getToken() {
        return token;
    }

    public String getAccountId() {
        return accountId;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
