package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.PasswordHash;

/**
 * 비밀번호 해싱/검증 outbound port. (요구사항 1.9)
 */
public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash passwordHash);
}
