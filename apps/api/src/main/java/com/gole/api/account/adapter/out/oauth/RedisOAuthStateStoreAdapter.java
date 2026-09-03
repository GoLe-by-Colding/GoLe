package com.gole.api.account.adapter.out.oauth;

import com.gole.api.account.application.port.out.OAuthStateStorePort;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 기반 OAuth state 저장소. key {@code oauth:state:<state>} → provider key. 1회 소비(get+delete).
 * (소셜 로그인 보안 강화 — 서버측 state 검증)
 */
@Component
public class RedisOAuthStateStoreAdapter implements OAuthStateStorePort {

    private static final String KEY_PREFIX = "oauth:state:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisOAuthStateStoreAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String state, OAuthStateContext context, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(state), objectMapper.writeValueAsString(context), ttl);
        } catch (JacksonException invalidContext) {
            throw new IllegalStateException("OAuth 요청 문맥을 저장할 수 없습니다", invalidContext);
        }
    }

    @Override
    public Optional<OAuthStateContext> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        String stored = redisTemplate.opsForValue().getAndDelete(key(state));
        if (stored == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(stored, OAuthStateContext.class));
        } catch (JacksonException corruptState) {
            return Optional.empty();
        }
    }

    private static String key(String state) {
        return KEY_PREFIX + state;
    }
}
