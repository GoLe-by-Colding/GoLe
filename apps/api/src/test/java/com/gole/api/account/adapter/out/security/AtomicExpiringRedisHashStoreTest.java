package com.gole.api.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class AtomicExpiringRedisHashStoreTest {

    @Test
    void deletesKeyAndFailsClosedWhenScriptCannotConfirmExpiry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("challenge-key")), any(Object[].class)))
                .thenReturn(0L);

        assertThatThrownBy(() -> AtomicExpiringRedisHashStore.store(
                        redis, "challenge-key", Map.of("codeHash", "hash"), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TTL");

        verify(redis).delete("challenge-key");
    }
}
