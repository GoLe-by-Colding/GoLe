package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort;
import com.gole.api.account.domain.model.Email;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 비밀번호 재설정 challenge 저장소.
 *
 * <p>키에는 이메일 원문을 남기지 않고 SHA-256 식별자를 사용한다. 코드도 BCrypt 해시만 저장하며, Lua로
 * 실패 횟수와 1회 소비를 원자적으로 처리한다.
 */
@Component
public class RedisPasswordResetChallengeStoreAdapter implements PasswordResetChallengeStorePort {

    private static final String KEY_PREFIX = "gole:password-reset:";
    private static final String ACCOUNT_ID = "accountId";
    private static final String CODE_HASH = "codeHash";
    private static final String ISSUED_AT = "issuedAt";
    private static final String FAILED_ATTEMPTS = "failedAttempts";

    private static final DefaultRedisScript<Long> INCREMENT_FAILURE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end "
                    + "return redis.call('HINCRBY', KEYS[1], 'failedAttempts', 1)",
            Long.class);

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'codeHash') ~= ARGV[1] then return 0 end "
                    + "return redis.call('DEL', KEYS[1])",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisPasswordResetChallengeStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(Email email, Challenge challenge, Duration ttl) {
        String key = key(email);
        redis.opsForHash()
                .putAll(
                        key,
                        Map.of(
                                ACCOUNT_ID, challenge.accountId(),
                                CODE_HASH, challenge.codeHash(),
                                ISSUED_AT, challenge.issuedAt().toString(),
                                FAILED_ATTEMPTS, Integer.toString(challenge.failedAttempts())));
        redis.expire(key, ttl);
    }

    @Override
    public Optional<Challenge> find(Email email) {
        Map<Object, Object> values = redis.opsForHash().entries(key(email));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Challenge(
                    required(values, ACCOUNT_ID),
                    required(values, CODE_HASH),
                    Instant.parse(required(values, ISSUED_AT)),
                    Integer.parseInt(required(values, FAILED_ATTEMPTS))));
        } catch (IllegalArgumentException exception) {
            // 손상된 challenge는 인증에 사용하지 않는다.
            delete(email);
            return Optional.empty();
        }
    }

    @Override
    public int incrementFailedAttempts(Email email) {
        Long result = redis.execute(INCREMENT_FAILURE_SCRIPT, List.of(key(email)));
        return result == null ? -1 : Math.toIntExact(result);
    }

    @Override
    public boolean consume(Email email, String expectedCodeHash) {
        Long result = redis.execute(CONSUME_SCRIPT, List.of(key(email)), expectedCodeHash);
        return result != null && result == 1L;
    }

    @Override
    public void delete(Email email) {
        redis.delete(key(email));
    }

    private static String required(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("password-reset challenge field is missing: " + field);
        }
        return text;
    }

    private static String key(Email email) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(email.value().getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
