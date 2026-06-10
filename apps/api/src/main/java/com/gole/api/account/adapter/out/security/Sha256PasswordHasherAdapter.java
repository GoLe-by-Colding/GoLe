package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.domain.model.PasswordHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 추가 의존성 없이 표준 라이브러리만으로 구현한 비밀번호 해셔. (요구사항 1.9)
 *
 * <p>SHA-256 + 랜덤 솔트. 해시 문자열 포맷: {@code sha256$<base64salt>$<base64hash>}.
 * spring-security-crypto가 클래스패스에 없어 BCrypt 대신 사용한다.
 */
@Component
public class Sha256PasswordHasherAdapter implements PasswordHasherPort {

    private static final String ALGORITHM = "sha256";
    private static final int SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public PasswordHash hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] digest = digest(rawPassword, salt);
        String encoded = ALGORITHM
                + "$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(digest);
        return new PasswordHash(encoded);
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        String[] parts = passwordHash.value().split("\\$");
        if (parts.length != 3 || !ALGORITHM.equals(parts[0])) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[1]);
            expected = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] actual = digest(rawPassword, salt);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] digest(String rawPassword, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
