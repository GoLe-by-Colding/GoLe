package com.gole.api.pricing.adapter.out.cache;

import com.gole.api.pricing.application.port.in.GetTrendingSetsUseCase.TrendingSet;
import com.gole.api.pricing.application.port.out.TrendingCachePort;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 인기 세트 랭킹 캐시 어댑터. (백로그 13.4)
 *
 * <p>특정 JSON 라이브러리(Jackson 2/3) 버전에 묶이지 않도록, 행마다
 * {@code base64(setNumber)|base64(name)|base64(imageUrl)|tradeCount|averagePrice}
 * 형식으로 자체 인코딩한다(문자열 필드는 Base64로 구분자 충돌 방지). 행 구분자는 개행.
 *
 * <p>Redis 장애가 기능을 중단시키지 않도록 모든 예외를 흡수한다
 * (조회 실패→캐시 미스, 저장 실패→무시).
 */
@Component
public class RedisTrendingCacheAdapter implements TrendingCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisTrendingCacheAdapter.class);
    private static final String KEY_PREFIX = "pricing:trending:v2:";

    private final StringRedisTemplate redisTemplate;

    public RedisTrendingCacheAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<List<TrendingSet>> get(int limit) {
        try {
            String raw = redisTemplate.opsForValue().get(key(limit));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(decode(raw));
        } catch (RuntimeException e) {
            log.warn("Trending cache read failed (treating as miss): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(int limit, List<TrendingSet> trending, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(limit), encode(trending), ttl);
        } catch (RuntimeException e) {
            log.warn("Trending cache write skipped: {}", e.getMessage());
        }
    }

    private static String encode(List<TrendingSet> trending) {
        StringBuilder sb = new StringBuilder();
        for (TrendingSet s : trending) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(b64(s.setNumber()))
                    .append('|')
                    .append(b64(s.name()))
                    .append('|')
                    .append(b64(s.imageUrl() == null ? "" : s.imageUrl()))
                    .append('|')
                    .append(s.tradeCount())
                    .append('|')
                    .append(s.averagePrice());
        }
        return sb.toString();
    }

    private static List<TrendingSet> decode(String raw) {
        List<TrendingSet> result = new ArrayList<>();
        if (raw.isEmpty()) {
            return result;
        }
        for (String row : raw.split("\n")) {
            String[] parts = row.split("\\|", -1);
            if (parts.length != 5) {
                continue; // 손상된 행은 건너뛴다
            }
            String imageUrl = unb64(parts[2]);
            result.add(new TrendingSet(
                    unb64(parts[0]),
                    unb64(parts[1]),
                    imageUrl.isEmpty() ? null : imageUrl,
                    Long.parseLong(parts[3]),
                    Long.parseLong(parts[4])));
        }
        return result;
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String key(int limit) {
        return KEY_PREFIX + limit;
    }
}
