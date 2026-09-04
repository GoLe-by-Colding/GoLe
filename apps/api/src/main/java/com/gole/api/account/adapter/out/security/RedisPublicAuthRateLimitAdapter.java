package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.PublicAuthRateLimitPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis Lua 한 번으로 모든 시간창을 검사한 뒤에만 카운터를 함께 증가시킨다. */
@Component
public class RedisPublicAuthRateLimitAdapter implements PublicAuthRateLimitPort {

    static final String KEY_PREFIX = "gole:public-auth-rate:v1:";

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "for index, key in ipairs(KEYS) do "
                    + "local current = tonumber(redis.call('GET', key) or '0') "
                    + "local maximum = tonumber(ARGV[(index - 1) * 2 + 1]) "
                    + "if current >= maximum then "
                    + "local retention = tonumber(ARGV[(index - 1) * 2 + 2]) "
                    + "if redis.call('PTTL', key) < 0 then redis.call('PEXPIRE', key, retention) end "
                    + "return index "
                    + "end "
                    + "end "
                    + "for index, key in ipairs(KEYS) do "
                    + "redis.call('INCR', key) "
                    + "local retention = tonumber(ARGV[(index - 1) * 2 + 2]) "
                    + "if redis.call('PTTL', key) < 0 then redis.call('PEXPIRE', key, retention) end "
                    + "end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisPublicAuthRateLimitAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision acquire(List<Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            throw new IllegalArgumentException("rate limit buckets must not be empty");
        }
        List<String> keys =
                buckets.stream().map(Bucket::key).map(KEY_PREFIX::concat).toList();
        List<String> arguments = new ArrayList<>(buckets.size() * 2);
        for (Bucket bucket : buckets) {
            arguments.add(Integer.toString(bucket.maximum()));
            arguments.add(Long.toString(bucket.retention().toMillis()));
        }

        Long rejected = redis.execute(ACQUIRE_SCRIPT, keys, arguments.toArray());
        if (rejected == null) {
            throw new IllegalStateException("Redis returned no public auth rate-limit decision");
        }
        if (rejected == 0L) {
            return Decision.allowedDecision();
        }
        int rejectedIndex = Math.toIntExact(rejected - 1);
        if (rejectedIndex < 0 || rejectedIndex >= buckets.size()) {
            throw new IllegalStateException("Redis returned an invalid public auth rate-limit bucket");
        }
        return Decision.rejectedDecision(
                rejectedIndex, buckets.get(rejectedIndex).retryAfter());
    }
}
