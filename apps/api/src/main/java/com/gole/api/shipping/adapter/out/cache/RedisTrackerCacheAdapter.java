package com.gole.api.shipping.adapter.out.cache;

import com.gole.api.shipping.application.port.out.DeliveryTrackerPort.TrackingResult;
import com.gole.api.shipping.application.port.out.TrackerCachePort;
import com.gole.api.shipping.domain.model.Carrier;
import com.gole.api.shipping.domain.model.DeliveryStatus;
import com.gole.api.shipping.domain.model.WaybillNumber;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 트래커 응답 캐시. (R2.5)
 *
 * <p>{@code RedisTrendingCacheAdapter}와 동일한 방침 — JSON 라이브러리에 묶이지 않는
 * 자체 인코딩({@code status|b64(rawStatus)}), 예외 전부 흡수(조회 실패→미스, 저장 실패→무시).
 */
@Component
public class RedisTrackerCacheAdapter implements TrackerCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackerCacheAdapter.class);
    private static final String KEY_PREFIX = "shipping:track:";

    private final StringRedisTemplate redisTemplate;

    public RedisTrackerCacheAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<TrackingResult> get(Carrier carrier, WaybillNumber waybill) {
        try {
            String raw = redisTemplate.opsForValue().get(key(carrier, waybill));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(decode(raw));
        } catch (RuntimeException e) {
            log.warn("Tracker cache read failed (treating as miss): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Carrier carrier, WaybillNumber waybill, TrackingResult result, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(carrier, waybill), encode(result), ttl);
        } catch (RuntimeException e) {
            log.warn("Tracker cache write skipped: {}", e.getMessage());
        }
    }

    private static String key(Carrier carrier, WaybillNumber waybill) {
        return KEY_PREFIX + carrier.name() + ":" + waybill.value();
    }

    private static String encode(TrackingResult result) {
        String raw = result.rawStatus() == null
                ? ""
                : Base64.getEncoder().encodeToString(result.rawStatus().getBytes(StandardCharsets.UTF_8));
        return result.status().name() + "|" + raw;
    }

    private static TrackingResult decode(String raw) {
        int sep = raw.indexOf('|');
        DeliveryStatus status = DeliveryStatus.valueOf(raw.substring(0, sep));
        String encoded = raw.substring(sep + 1);
        String rawStatus =
                encoded.isEmpty() ? null : new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        return new TrackingResult(status, rawStatus);
    }
}
