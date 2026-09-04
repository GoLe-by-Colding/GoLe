package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.AccountDeletionVerificationStorePort;
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

/** Redis 기반 탈퇴 본인확인 challenge. 계정 ID와 코드 원문은 Redis 키·값에 남기지 않는다. */
@Component
public class RedisAccountDeletionVerificationStoreAdapter implements AccountDeletionVerificationStorePort {

    private static final String KEY_PREFIX = "gole:account-deletion-verification:";
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

    public RedisAccountDeletionVerificationStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void store(String accountId, Challenge challenge, Duration ttl) {
        AtomicExpiringRedisHashStore.store(
                redis,
                key(accountId),
                Map.of(
                        CODE_HASH, challenge.codeHash(),
                        ISSUED_AT, challenge.issuedAt().toString(),
                        FAILED_ATTEMPTS, Integer.toString(challenge.failedAttempts())),
                ttl);
    }

    @Override
    public Optional<Challenge> find(String accountId) {
        Map<Object, Object> values = redis.opsForHash().entries(key(accountId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Challenge(
                    required(values, CODE_HASH),
                    Instant.parse(required(values, ISSUED_AT)),
                    Integer.parseInt(required(values, FAILED_ATTEMPTS))));
        } catch (IllegalArgumentException exception) {
            delete(accountId);
            return Optional.empty();
        }
    }

    @Override
    public int incrementFailedAttempts(String accountId) {
        Long result = redis.execute(INCREMENT_FAILURE_SCRIPT, List.of(key(accountId)));
        return result == null ? -1 : Math.toIntExact(result);
    }

    @Override
    public boolean consume(String accountId, String expectedCodeHash) {
        Long result = redis.execute(CONSUME_SCRIPT, List.of(key(accountId)), expectedCodeHash);
        return result != null && result == 1L;
    }

    @Override
    public void delete(String accountId) {
        redis.delete(key(accountId));
    }

    private static String required(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("account-deletion challenge field is missing: " + field);
        }
        return text;
    }

    private static String key(String accountId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(accountId.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
