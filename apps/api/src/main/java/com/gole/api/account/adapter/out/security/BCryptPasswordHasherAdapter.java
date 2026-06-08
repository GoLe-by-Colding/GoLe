package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.PasswordHash;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 기본 비밀번호 해셔. BCrypt(자동 솔트, work factor 10)를 사용한다. (요구사항 1.9, 1.12)
 *
 * <p>마이그레이션 전략: 기존 계정은 {@code sha256$...} 포맷으로 저장되어 있을 수 있다.
 * {@link #matches}는 BCrypt 포맷({@code $2...})이면 BCrypt로, 그 외에는 레거시 SHA-256
 * 어댑터로 위임 검증한다. 레거시 해시는 로그인 성공 시 {@link #needsRehash}가 true를 반환해
 * {@code AccountService}가 BCrypt로 재해시·저장하도록 한다(점진 전환).
 */
@Component
@Primary
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private static final String BCRYPT_PREFIX = "$2";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Sha256PasswordHasherAdapter legacyHasher;

    public BCryptPasswordHasherAdapter(Sha256PasswordHasherAdapter legacyHasher) {
        this.legacyHasher = legacyHasher;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        if (isBcrypt(passwordHash)) {
            return encoder.matches(rawPassword, passwordHash.value());
        }
        // 레거시(SHA-256 등) 해시는 기존 어댑터로 검증한다.
        return legacyHasher.matches(rawPassword, passwordHash);
    }

    @Override
    public boolean needsRehash(PasswordHash passwordHash) {
        return passwordHash != null && !isBcrypt(passwordHash);
    }

    private static boolean isBcrypt(PasswordHash passwordHash) {
        return passwordHash.value().startsWith(BCRYPT_PREFIX);
    }
}
