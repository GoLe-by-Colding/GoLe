package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.PasswordHash;

/**
 * 비밀번호 해싱/검증 outbound port. (요구사항 1.9)
 */
public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash passwordHash);

    /**
     * 저장된 해시가 더 강한 최신 알고리즘으로 재해시되어야 하는지 여부. (요구사항 1.12)
     *
     * <p>레거시(예: SHA-256) 해시를 로그인 성공 시 점진적으로 BCrypt로 승격하기 위한 신호.
     * 기본 구현은 승격 불필요(false)를 반환하여 단순 해셔/테스트 페이크가 영향을 받지 않는다.
     */
    default boolean needsRehash(PasswordHash passwordHash) {
        return false;
    }
}
