package com.gole.api.account.domain.model;

import com.gole.api.common.exception.BadRequestException;
import java.util.Locale;
import java.util.Objects;

/**
 * 계정 표시 이름 값 객체. (onboarding D9)
 *
 * <p>2~12자, 한글/영문/숫자만 — 공백·특수문자는 받지 않는다. 유일성은 대소문자를 무시하므로
 * 비교용 키({@link #normalized()})를 도메인이 직접 제공한다. 어댑터마다 lower-casing을
 * 다시 구현하면 "GoLe"과 "gole"이 서로 다른 계정으로 새는 순간이 온다.
 */
public record Nickname(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 12;

    public Nickname {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new BadRequestException("INVALID_NICKNAME", "닉네임은 2~12자여야 합니다");
        }
        if (!trimmed.matches("[가-힣a-zA-Z0-9]+")) {
            throw new BadRequestException("INVALID_NICKNAME", "닉네임에는 한글·영문·숫자만 사용할 수 있습니다");
        }
        value = trimmed;
    }

    /** 대소문자 무시 유일성 비교에 쓰는 정규화 키. */
    public String normalized() {
        return value.toLowerCase(Locale.ROOT);
    }

    /** null 허용 생성 헬퍼. 빈 값이면 null을 반환한다. */
    public static Nickname ofNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new Nickname(raw);
    }
}
