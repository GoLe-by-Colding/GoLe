package com.gole.api.media.adapter.out.ratelimit;

import com.gole.api.media.application.port.out.MediaUploadQuotaPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 시간창 업로드 제한. 시간창 번호를 키에 포함해 TTL 설정 사이 장애가 나도 다음 창의
 * 사용자를 영구 차단하지 않는다.
 */
@Component
public class RedisMediaUploadQuotaAdapter implements MediaUploadQuotaPort {

    private static final String KEY_PREFIX = "gole:media-upload-quota:";

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisMediaUploadQuotaAdapter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public Decision acquire(String accountId, int imageCount, int maximumImages, Duration window) {
        long windowMillis = window.toMillis();
        long nowMillis = clock.millis();
        long bucket = Math.floorDiv(nowMillis, windowMillis);
        String key = KEY_PREFIX + accountId + ":" + bucket;

        redis.opsForValue().setIfAbsent(key, "0", window.multipliedBy(2));
        Long used = redis.opsForValue().increment(key, imageCount);
        long retryMillis = windowMillis - Math.floorMod(nowMillis, windowMillis);
        return new Decision(used != null && used <= maximumImages, Duration.ofMillis(Math.max(1, retryMillis)));
    }
}
