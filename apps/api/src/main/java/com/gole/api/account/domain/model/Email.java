package com.gole.api.account.domain.model;

import java.util.regex.Pattern;

/**
 * 이메일 값 객체. 생성 시 형식을 검증하고 정규화(소문자/trim)한다.
 */
public record Email(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        value = value.trim().toLowerCase();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid email format");
        }
    }
}
