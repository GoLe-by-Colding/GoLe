package com.gole.api.collection.application.service;

import com.gole.api.collection.application.port.in.RecordCollectionValueSnapshotsUseCase;
import com.gole.api.collection.application.port.in.RecordCollectionValueSnapshotsUseCase.RecordSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 컬렉션 자산 스냅샷을 찍는다. (collection-value-history H2)
 *
 * <p>저장소의 첫 {@code cron} 잡이다. 운영은 백엔드 컨테이너 하나라 분산 잠금을 두지 않는다 — 스냅샷이
 * 사용자·날짜 기준 덮어쓰기라, 두 번 돌아도 결과가 같다.
 */
@Component
public class CollectionValueSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectionValueSnapshotScheduler.class);

    private final RecordCollectionValueSnapshotsUseCase snapshots;
    private final CollectionValueSnapshotProperties properties;
    private final Clock clock;

    public CollectionValueSnapshotScheduler(
            RecordCollectionValueSnapshotsUseCase snapshots,
            CollectionValueSnapshotProperties properties,
            Clock clock) {
        this.snapshots = snapshots;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${gole.collection.value-snapshot.cron:0 30 4 * * *}",
            zone = "${gole.collection.value-snapshot.zone:Asia/Seoul}")
    public void run() {
        if (!properties.enabled()) {
            return;
        }
        runOnce(Instant.now(clock));
    }

    /** 테스트·수동 실행에서 시각을 고정해 부를 수 있게 분리한다. 켜짐 여부는 보지 않는다. */
    public RecordSummary runOnce(Instant now) {
        long started = System.nanoTime();
        RecordSummary summary = snapshots.recordAll(now);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        if (summary.failed() > 0) {
            log.warn(
                    "[collection-value-snapshot] 완료(실패 포함) targets={} succeeded={} failed={} elapsedMs={}",
                    summary.targets(),
                    summary.succeeded(),
                    summary.failed(),
                    elapsedMs);
        } else {
            log.info(
                    "[collection-value-snapshot] 완료 targets={} succeeded={} elapsedMs={}",
                    summary.targets(),
                    summary.succeeded(),
                    elapsedMs);
        }
        return summary;
    }
}
