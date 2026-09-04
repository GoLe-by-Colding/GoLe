package com.gole.api.media.application.service;

import com.gole.api.media.application.port.out.MediaDeletionOutboxPort;
import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.domain.model.MediaDeletionTask;
import com.gole.api.media.domain.model.MediaKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mongo 트랜잭션에 기록된 삭제 outbox를 MinIO에 멱등 적용한다.
 *
 * <p>객체 삭제 뒤 완료 저장 전에 프로세스가 죽어도 다음 실행이 같은 원본과 파생물을 다시 삭제하므로
 * 개인정보가 부활하지 않는다. 오류 메시지에는 키나 사용자 입력을 기록하지 않는다.
 */
@Component
public class MediaDeletionWorker {

    private static final Logger log = LoggerFactory.getLogger(MediaDeletionWorker.class);
    private static final Duration MAX_BACKOFF = Duration.ofHours(24);

    private final MediaDeletionOutboxPort deletions;
    private final ObjectStoragePort storage;
    private final MediaAssetLifecycleService lifecycle;
    private final MediaLifecycleProperties properties;
    private final Clock clock;
    private final AtomicBoolean restoreReplayComplete = new AtomicBoolean(false);

    public MediaDeletionWorker(
            MediaDeletionOutboxPort deletions,
            ObjectStoragePort storage,
            MediaAssetLifecycleService lifecycle,
            MediaLifecycleProperties properties,
            Clock clock) {
        this.deletions = deletions;
        this.storage = storage;
        this.lifecycle = lifecycle;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gole.media.lifecycle.maintenance-interval:PT1M}")
    public void maintain() {
        lifecycle.revokeExpiredStages();
        replayCompletedJournalAfterRestore();
        Instant now = Instant.now(clock);
        deletions.findDue(now, properties.batchSize()).forEach(task -> process(task, now));
    }

    void process(MediaDeletionTask task, Instant now) {
        try {
            deleteObjects(task.mediaKey());
            deletions.save(task.complete(now));
        } catch (RuntimeException failure) {
            Duration delay = backoff(task.attempts());
            deletions.save(
                    task.retry(now, delay, failure.getClass().getSimpleName(), properties.deletionMaximumAttempts()));
            log.warn("Media object deletion failed; durable outbox will retry (attempt={})", task.attempts() + 1);
        }
    }

    private void replayCompletedJournalAfterRestore() {
        if (restoreReplayComplete.get() || properties.replayCompletedSince().isBlank()) {
            return;
        }
        final Instant since;
        try {
            since = Instant.parse(properties.replayCompletedSince());
        } catch (DateTimeParseException invalidConfiguration) {
            throw new IllegalStateException("GOLE_MEDIA_REPLAY_COMPLETED_SINCE must be an ISO-8601 instant");
        }
        for (MediaDeletionTask task : deletions.findCompletedSince(since)) {
            deleteObjects(task.mediaKey());
        }
        restoreReplayComplete.set(true);
    }

    private void deleteObjects(String key) {
        storage.delete(key);
        storage.deletePrefix(MediaKey.derivativePrefix(key));
    }

    private Duration backoff(int attempts) {
        int shift = Math.min(Math.max(attempts, 0), 16);
        long multiplier = 1L << shift;
        Duration candidate;
        try {
            candidate = properties.deletionInitialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            return MAX_BACKOFF;
        }
        return candidate.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : candidate;
    }
}
