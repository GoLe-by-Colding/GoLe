package com.gole.api.account.adapter.out.oauth;

import com.gole.api.account.application.port.out.OAuthStateStorePort;
import com.gole.api.account.domain.model.AuthProvider;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 OAuth state 저장소. key {@code oauth:state:<state>} → provider key. 1회 소비(get+delete).
 * (소셜 로그인 보안 강화 — 서버측 state 검증)
 */
@Component
public class RedisOAuthStateStoreAdapter implements OAuthStateStorePort {

    private static final String KEY_PREFIX = "oauth:state:";

    private final StringRedisTemplate redisTemplate;

    public RedisOAuthStateStoreAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String state, AuthProvider provider, Duration ttl) {
        redisTemplate.opsForValue().set(key(state), provider.key(), ttl);
    }

    @Override
    public boolean consume(String state, AuthProvider provider) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String storedProvider = redisTemplate.opsForValue().getAndDelete(key(state));
        return storedProvider != null && storedProvider.equals(provider.key());
    }

    private static String key(String state) {
        return KEY_PREFIX + state;
    }
}
