package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gole.api.media.application.port.out.MediaDeletionOutboxPort;
import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.domain.model.MediaDeletionTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MediaDeletionWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String KEY = "images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg";

    @Test
    void storageFailure_isRetriedThenOriginalAndDerivativesAreDeletedIdempotently() {
        FakeOutbox outbox = new FakeOutbox();
        FakeStorage storage = new FakeStorage();
        storage.fail = true;
        MediaDeletionWorker worker = worker(outbox, storage, 8, "");
        MediaDeletionTask task = MediaDeletionTask.pending(KEY, NOW);

        worker.process(task, NOW);
        MediaDeletionTask retry = outbox.saved.get(KEY);
        assertThat(retry.status()).isEqualTo(MediaDeletionTask.Status.PENDING);
        assertThat(retry.attempts()).isEqualTo(1);

        storage.fail = false;
        worker.process(retry, NOW.plusSeconds(31));
        worker.process(outbox.saved.get(KEY), NOW.plusSeconds(32));

        assertThat(outbox.saved.get(KEY).status()).isEqualTo(MediaDeletionTask.Status.COMPLETED);
        assertThat(storage.deleted).contains(KEY);
        assertThat(storage.deletedPrefixes).contains("derivatives/" + KEY + "/");
    }

    @Test
    void maximumAttempts_movesTaskToDeadLetterWithoutRawErrorText() {
        FakeOutbox outbox = new FakeOutbox();
        FakeStorage storage = new FakeStorage();
        storage.fail = true;
        MediaDeletionWorker worker = worker(outbox, storage, 1, "");

        worker.process(MediaDeletionTask.pending(KEY, NOW), NOW);

        MediaDeletionTask failed = outbox.saved.get(KEY);
        assertThat(failed.status()).isEqualTo(MediaDeletionTask.Status.DEAD_LETTER);
        assertThat(failed.lastErrorCode()).isEqualTo("IllegalStateException");
        assertThat(failed.lastErrorCode()).doesNotContain(KEY);
    }

    @Test
    void restoreReplay_reappliesCompletedJournalOnlyOncePerProcess() {
        FakeOutbox outbox = new FakeOutbox();
        FakeStorage storage = new FakeStorage();
        MediaDeletionTask completed = MediaDeletionTask.pending(KEY, NOW).complete(NOW.plusSeconds(1));
        outbox.completed = List.of(completed);
        MediaDeletionWorker worker = worker(outbox, storage, 8, "2026-09-04T00:00:00Z");

        worker.maintain();
        worker.maintain();

        assertThat(storage.deleted).containsExactly(KEY);
        assertThat(storage.deletedPrefixes).containsExactly("derivatives/" + KEY + "/");
    }

    private MediaDeletionWorker worker(
            FakeOutbox outbox, FakeStorage storage, int maximumAttempts, String replaySince) {
        MediaLifecycleProperties properties = new MediaLifecycleProperties(
                Duration.ofHours(24), Duration.ofMinutes(1), 100, maximumAttempts, Duration.ofSeconds(30), replaySince);
        return new MediaDeletionWorker(
                outbox, storage, mock(MediaAssetLifecycleService.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeOutbox implements MediaDeletionOutboxPort {
        private final Map<String, MediaDeletionTask> saved = new HashMap<>();
        private List<MediaDeletionTask> completed = List.of();

        @Override
        public void enqueueIfAbsent(MediaDeletionTask task) {
            saved.putIfAbsent(task.mediaKey(), task);
        }

        @Override
        public void requeue(MediaDeletionTask task) {
            saved.put(task.mediaKey(), task);
        }

        @Override
        public List<MediaDeletionTask> findDue(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<MediaDeletionTask> findCompletedSince(Instant since) {
            return completed;
        }

        @Override
        public void save(MediaDeletionTask task) {
            saved.put(task.mediaKey(), task);
        }
    }

    private static final class FakeStorage implements ObjectStoragePort {
        private boolean fail;
        private final List<String> deleted = new ArrayList<>();
        private final List<String> deletedPrefixes = new ArrayList<>();

        @Override
        public void ensureBucket() {}

        @Override
        public void put(String key, byte[] content, String contentType) {}

        @Override
        public Optional<StoredObject> get(String key) {
            return Optional.empty();
        }

        @Override
        public void delete(String key) {
            if (fail) {
                throw new IllegalStateException("sensitive raw provider error for " + key);
            }
            deleted.add(key);
        }

        @Override
        public void deletePrefix(String prefix) {
            if (fail) {
                throw new IllegalStateException("sensitive raw provider error for " + prefix);
            }
            deletedPrefixes.add(prefix);
        }
    }
}
