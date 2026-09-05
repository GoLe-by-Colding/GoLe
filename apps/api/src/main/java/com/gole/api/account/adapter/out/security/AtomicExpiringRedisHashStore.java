package com.gole.api.account.adapter.out.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** TTL 없는 인증 hash가 남지 않도록 hash 교체와 만료 설정을 한 Redis script로 처리한다. */
final class AtomicExpiringRedisHashStore {

    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>(
            "local ttl = tonumber(ARGV[1]); "
                    + "if (not ttl) or ttl <= 0 or #ARGV < 3 or ((#ARGV - 1) % 2 ~= 0) then "
                    + "redis.call('DEL', KEYS[1]); return 0 end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "redis.call('HSET', KEYS[1], unpack(ARGV, 2)); "
                    + "if redis.call('PEXPIRE', KEYS[1], ttl) ~= 1 then "
                    + "redis.call('DEL', KEYS[1]); return 0 end; "
                    + "return 1",
            Long.class);

    private AtomicExpiringRedisHashStore() {}

    static void store(StringRedisTemplate redis, String key, Map<String, String> values, Duration ttl) {
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(ttl, "ttl");
        if (values.isEmpty()) {
            IllegalArgumentException failure = new IllegalArgumentException("expiring Redis hash must not be empty");
            deleteQuietly(redis, key, failure);
            throw failure;
        }

        long ttlMillis;
        try {
            ttlMillis = ttl.toMillis();
        } catch (ArithmeticException exception) {
            IllegalArgumentException failure = new IllegalArgumentException("Redis hash TTL is too large", exception);
            deleteQuietly(redis, key, failure);
            throw failure;
        }
        if (ttlMillis <= 0) {
            IllegalArgumentException failure = new IllegalArgumentException("Redis hash TTL must be positive");
            deleteQuietly(redis, key, failure);
            throw failure;
        }

        List<String> arguments = new ArrayList<>(1 + values.size() * 2);
        arguments.add(Long.toString(ttlMillis));
        values.forEach((field, value) -> {
            arguments.add(Objects.requireNonNull(field, "Redis hash field"));
            arguments.add(Objects.requireNonNull(value, "Redis hash value"));
        });

        Long result;
        try {
            result = redis.execute(STORE_SCRIPT, List.of(key), arguments.toArray());
        } catch (RuntimeException failure) {
            deleteQuietly(redis, key, failure);
            throw failure;
        }
        if (result == null || result != 1L) {
            IllegalStateException failure = new IllegalStateException("Redis hash TTL could not be applied");
            deleteQuietly(redis, key, failure);
            throw failure;
        }
    }

    private static void deleteQuietly(StringRedisTemplate redis, String key, RuntimeException failure) {
        try {
            redis.delete(key);
        } catch (RuntimeException cleanupFailure) {
            if (failure != null && cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
