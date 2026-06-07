package com.gole.api.account.domain.model;

/**
 * 단방향 솔트 해시 결과 값 객체. 평문 비밀번호는 도메인에 보관하지 않는다. (요구사항 1.9)
 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("password hash must not be blank");
        }
    }
}
