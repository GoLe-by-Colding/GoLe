package com.gole.api.media.bootstrap;

import com.gole.api.media.application.port.out.ObjectStoragePort;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 시드 미디어 적재기. 번들된 GoLe 오리지널 커버 SVG(공식 LEGO 이미지 아님 — ip-safe-content)를
 * MinIO 버킷에 멱등 업로드한다. 카탈로그/커뮤니티 시더가 참조하는 안정 키를 보장한다.
 *
 * <p>키 규칙: classpath {@code seed-media/<path>} → 객체 키 {@code <path>}
 * (예: {@code seed-media/catalog/10307.svg} → {@code catalog/10307.svg}).
 * 공개 URL은 {@code GET /api/v1/media/catalog/10307.svg} 로 서빙된다.
 *
 * <p>{@code gole.media.seed-on-startup=false} 로 비활성화(테스트 격리).
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "gole.media.seed-on-startup", havingValue = "true")
public class MediaSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaSeeder.class);

    private static final String LOCATION_PATTERN = "classpath:seed-media/**/*.svg";
    private static final String KEY_PREFIX = "seed-media/";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";

    private final ObjectStoragePort objectStorage;

    public MediaSeeder(ObjectStoragePort objectStorage) {
        this.objectStorage = objectStorage;
    }

    @Override
    public void run(String... args) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.warn("[seed] media: 시드 리소스 조회 실패, 건너뜀: {}", e.getMessage());
            return;
        }

        int uploaded = 0;
        for (Resource resource : resources) {
            String key = keyFor(resource);
            if (key == null) {
                continue;
            }
            try {
                // 멱등: 이미 존재하면 재업로드하지 않는다.
                if (objectStorage.get(key).isPresent()) {
                    continue;
                }
                byte[] content = StreamUtils.copyToByteArray(resource.getInputStream());
                objectStorage.put(key, content, SVG_CONTENT_TYPE);
                uploaded += 1;
            } catch (IOException | RuntimeException e) {
                // 스토리지 장애가 부팅을 막지 않도록 개별 실패는 경고만 한다.
                log.warn("[seed] media: '{}' 업로드 실패: {}", key, e.getMessage());
            }
        }
        if (uploaded > 0) {
            log.info("[seed] media: {}개 시드 이미지 MinIO 업로드", uploaded);
        }
    }

    /** classpath URL에서 {@code seed-media/} 이후를 객체 키로 추출한다. */
    private String keyFor(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int idx = url.indexOf(KEY_PREFIX);
            if (idx < 0) {
                return null;
            }
            return url.substring(idx + KEY_PREFIX.length());
        } catch (IOException e) {
            return null;
        }
    }
}
