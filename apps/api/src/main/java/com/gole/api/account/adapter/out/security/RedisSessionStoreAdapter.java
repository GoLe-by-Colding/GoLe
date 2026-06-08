package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.domain.model.Role;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 저장소 어댑터. 키 {@code gole:session:<token>} 에
 * {@code <accountId>|<ROLE>} 형태로 저장한다(TTL 적용).
 */
@Component
public class RedisSessionStoreAdapter implements SessionStorePort {

    private static final String KEY_PREFIX = "gole:session:";

    private final StringRedisTemplate redis;

    public RedisSessionStoreAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String token) {
        return KEY_PREFIX + token;
    }

    @Override
    public void store(String token, String accountId, Role role, Duration ttl) {
        redis.opsForValue().set(key(token), accountId + "|" + role.name(), ttl);
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
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(key(token));
        }
    }
}
