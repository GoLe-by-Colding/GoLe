package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.domain.model.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 저장소 어댑터. 키 {@code gole:session:<token>} 에
 * {@code v2|<accountId>|<ROLE>|<issuedAt epoch>|<rotatedAt epoch>} 형태로 저장한다(TTL 적용).
 * 기존 {@code <accountId>|<ROLE>} 값도 자연 만료될 때까지 읽을 수 있다.
 *
 * <p>계정 단위 폐기(정지·권한 변경)를 위해 {@code gole:session:acct:<accountId>} 셋에 해당 계정의
 * 토큰 목록을 함께 유지한다. 인덱스에 남은 만료 토큰은 폐기 시 무해하게 무시되며, 인덱스가 유실되더라도
 * 세션 해석 단계의 계정 상태 검사(요구사항 6.5)가 최종 방어선이 된다.
 */
@Component
public class RedisSessionStoreAdapter implements SessionStorePort {

    private static final String KEY_PREFIX = "gole:session:";
    private static final String ACCOUNT_INDEX_PREFIX = "gole:session:acct:";

    private final StringRedisTemplate redis;

    public RedisSessionStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }

    private static String accountIndexKey(String accountId) {
        return ACCOUNT_INDEX_PREFIX + accountId;
    }

    @Override
    public void store(String token, String accountId, Role role, Duration ttl) {
        Instant now = Instant.now();
        store(token, accountId, role, now, now, ttl);
    }

    @Override
    public void store(String token, String accountId, Role role, Instant issuedAt, Instant rotatedAt, Duration ttl) {
        String value = String.join(
                "|",
                "v2",
                accountId,
                role.name(),
                Long.toString(issuedAt.getEpochSecond()),
                Long.toString(rotatedAt.getEpochSecond()));
        redis.opsForValue().set(key(token), value, ttl);
        String indexKey = accountIndexKey(accountId);
        redis.opsForSet().add(indexKey, token);
        // 짧게 남은 옛 세션 저장이 새 세션의 계정 인덱스를 줄이지 않도록 TTL은 늘리기만 한다.
        extendAccountIndex(indexKey, ttl);
    }

    @Override
    public Optional<SessionPrincipal> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().get(key(token));
        if (value == null) {
            return Optional.empty();
        }
        if (value.startsWith("v2|")) {
            return parseV2(value);
        }
        int sep = value.lastIndexOf('|');
        if (sep < 0) {
            return Optional.empty();
        }
        String accountId = value.substring(0, sep);
        Role role;
        try {
            role = Role.valueOf(value.substring(sep + 1));
        } catch (IllegalArgumentException ex) {
            role = Role.USER;
        }
        return Optional.of(new SessionPrincipal(accountId, role));
    }

    @Override
    public void touch(String token, String accountId, Duration ttl) {
        if (token == null || token.isBlank() || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        if (Boolean.TRUE.equals(redis.expire(key(token), ttl))) {
            extendAccountIndex(accountIndexKey(accountId), ttl);
        }
    }

    @Override
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        // 어느 계정의 토큰인지 먼저 확인해 인덱스에서도 제거한다(고아 항목 방지).
        resolve(token).ifPresent(principal -> redis.opsForSet().remove(accountIndexKey(principal.accountId()), token));
        redis.delete(key(token));
    }

    @Override
    public void revokeAllForAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        String indexKey = accountIndexKey(accountId);
        Set<String> tokens = redis.opsForSet().members(indexKey);
        if (tokens != null && !tokens.isEmpty()) {
            Set<String> keys = new HashSet<>();
            for (String token : tokens) {
                keys.add(key(token));
            }
            redis.delete(keys);
        }
        redis.delete(indexKey);
    }

    private static Optional<SessionPrincipal> parseV2(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 5 || parts[1].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SessionPrincipal(
                    parts[1],
                    Role.valueOf(parts[2]),
                    Instant.ofEpochSecond(Long.parseLong(parts[3])),
                    Instant.ofEpochSecond(Long.parseLong(parts[4]))));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void extendAccountIndex(String indexKey, Duration ttl) {
        Long currentMillis = redis.getExpire(indexKey, TimeUnit.MILLISECONDS);
        if (currentMillis == null || currentMillis < 0 || ttl.toMillis() > currentMillis) {
            redis.expire(indexKey, ttl);
        }
    }
}
