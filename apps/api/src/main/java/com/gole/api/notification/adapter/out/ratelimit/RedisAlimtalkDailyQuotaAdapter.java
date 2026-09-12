package com.gole.api.notification.adapter.out.ratelimit;

import com.gole.api.notification.application.port.out.AlimtalkDailyQuotaPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** TTL 설정 실패가 다음 시간창까지 영구 차단으로 이어지지 않는 Redis 고정 시간창 쿼터. */
@Component
public class RedisAlimtalkDailyQuotaAdapter implements AlimtalkDailyQuotaPort {

    private static final String KEY_PREFIX = "gole:interest-tag-alimtalk-quota:";

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisAlimtalkDailyQuotaAdapter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public boolean acquire(String accountId, int maximum, Duration window) {
        long windowMillis = window.toMillis();
        long bucket = Math.floorDiv(clock.millis(), windowMillis);
        String key = KEY_PREFIX + accountId + ":" + bucket;

        redis.opsForValue().setIfAbsent(key, "0", window.multipliedBy(2));
        Long used = redis.opsForValue().increment(key);
        return used != null && used <= maximum;
    }
}
