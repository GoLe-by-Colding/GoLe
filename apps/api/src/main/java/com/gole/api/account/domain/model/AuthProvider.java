package com.gole.api.account.domain.model;

import java.util.Locale;
import java.util.Optional;

/**
 * 소셜 로그인 제공자. (소셜 로그인 스펙 S1)
 */
public enum AuthProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    /** 경로 변수 등 외부 문자열을 대소문자 무시로 파싱한다. 미지원이면 빈 Optional. */
    public static Optional<AuthProvider> from(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AuthProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 설정 키/응답에 쓰는 소문자 식별자. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
