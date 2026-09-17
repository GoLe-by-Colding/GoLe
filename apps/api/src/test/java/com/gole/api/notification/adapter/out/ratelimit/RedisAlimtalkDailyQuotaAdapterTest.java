package com.gole.api.notification.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAlimtalkDailyQuotaAdapterTest {

    private static final Duration WINDOW = Duration.ofDays(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-10T23:59:59.999Z"));
    private RedisAlimtalkDailyQuotaAdapter quota;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Map<String, Long> counters = new HashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> counters.putIfAbsent(invocation.getArgument(0), 0L) == null);
        when(values.increment(anyString()))
                .thenAnswer(invocation -> counters.merge(invocation.getArgument(0), 1L, Long::sum));
        quota = new RedisAlimtalkDailyQuotaAdapter(redis, clock);
    }

    @Test
    void enforcesMaximumInsideOneFixedWindow() {
        assertThat(quota.acquire("account-a", 3, WINDOW)).isTrue();
        assertThat(quota.acquire("account-a", 3, WINDOW)).isTrue();
        assertThat(quota.acquire("account-a", 3, WINDOW)).isTrue();
        assertThat(quota.acquire("account-a", 3, WINDOW)).isFalse();
    }

    @Test
    void startsFreshCounterAtExactWindowBoundary() {
        assertThat(quota.acquire("account-a", 1, WINDOW)).isTrue();
        assertThat(quota.acquire("account-a", 1, WINDOW)).isFalse();

        clock.setInstant(Instant.parse("2026-09-11T00:00:00Z"));

        assertThat(quota.acquire("account-a", 1, WINDOW)).isTrue();
    }

    @Test
    void separatesCountersByAccount() {
        assertThat(quota.acquire("account-a", 1, WINDOW)).isTrue();
        assertThat(quota.acquire("account-a", 1, WINDOW)).isFalse();

        assertThat(quota.acquire("account-b", 1, WINDOW)).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
