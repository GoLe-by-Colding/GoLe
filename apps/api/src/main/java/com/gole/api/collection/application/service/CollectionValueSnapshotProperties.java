package com.gole.api.collection.application.service;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 컬렉션 자산 추이 스냅샷 설정. (collection-value-history H2)
 *
 * <p>record가 아닌 세터 바인딩 클래스인 이유는 {@code PipelineProperties}와 같다 — AOP 프록시 대상이 되면
 * final인 record는 CGLIB 서브클래싱이 안 돼 부팅이 실패한다.
 *
 * <p>{@code cron}은 스케줄러의 {@code @Scheduled}가 같은 키를 직접 읽는다. 여기 두는 것은 기본값을 한곳에
 * 적어 두기 위해서다.
 */
@ConfigurationProperties(prefix = "gole.collection.value-snapshot")
public class CollectionValueSnapshotProperties {

    static final String DEFAULT_ZONE = "Asia/Seoul";

    /** 끄면 정기 스냅샷만 멈춘다. 조회 시 오늘 점 갱신(H4)은 그대로 돈다. */
    private boolean enabled = true;

    private String cron = "0 30 4 * * *";

    /** 날짜를 가르는 시간대. 스냅샷의 {@code date}와 cron 모두 이 기준이다. */
    private String zone = DEFAULT_ZONE;

    /** 대상 사용자 커서 페이지 크기. */
    private int pageSize = 200;

    public boolean enabled() {
        return enabled;
    }

    public String cron() {
        return cron;
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone == null || zone.isBlank() ? DEFAULT_ZONE : zone.trim());
    }

    public int pageSize() {
        return Math.clamp(pageSize, 1, 1_000);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
