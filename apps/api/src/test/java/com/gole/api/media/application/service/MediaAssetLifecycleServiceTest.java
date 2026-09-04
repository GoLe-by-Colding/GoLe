package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.media.application.port.out.MediaAssetRepositoryPort;
import com.gole.api.media.application.port.out.MediaDeletionOutboxPort;
import com.gole.api.media.domain.exception.ImageNotFoundException;
import com.gole.api.media.domain.exception.InvalidMediaReferenceException;
import com.gole.api.media.domain.model.MediaAsset;
import com.gole.api.media.domain.model.MediaAssetStatus;
import com.gole.api.media.domain.model.MediaDeletionTask;
import com.gole.api.media.domain.model.MediaTargetType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaAssetLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String KEY_A = "images/0194f1c0-15ab-4f33-9b1d-34073d9d7738.jpg";
    private static final String KEY_B = "images/0194f1c0-15ab-4f33-9b1d-34073d9d7739.png";

    private InMemoryAssets assets;
    private InMemoryDeletions deletions;
    private MediaAssetLifecycleService service;

    @BeforeEach
    void setUp() {
        assets = new InMemoryAssets();
        deletions = new InMemoryDeletions();
        service = serviceAt(NOW);
    }

    @Test
    void stagedAsset_isOnlyReadableByOwner_thenBecomesPublicAfterSingleAttach() {
        service.registerStaged("owner-1", KEY_A, "image/jpeg", 12);

        assertThatCode(() -> service.requireReadable(KEY_A, Optional.of("owner-1")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireReadable(KEY_A, Optional.empty()))
                .isInstanceOf(ImageNotFoundException.class);
        assertThatThrownBy(() -> service.requireReadable(KEY_A, Optional.of("owner-2")))
                .isInstanceOf(ImageNotFoundException.class);

        service.replaceReferences("owner-1", MediaTargetType.LISTING, "listing-1", List.of(KEY_A), true);

        assertThatCode(() -> service.requireReadable(KEY_A, Optional.empty())).doesNotThrowAnyException();
        assertThat(assets.byKey.get(KEY_A).status()).isEqualTo(MediaAssetStatus.PUBLIC);
    }

    @Test
    void attach_rejectsExternalUrl_crossUser_duplicateAndSecondTarget() {
        service.registerStaged("owner-1", KEY_A, "image/jpeg", 12);

        assertThatThrownBy(() -> service.replaceReferences(
                        "owner-1",
                        MediaTargetType.LISTING,
                        "listing-1",
                        List.of("https://tracker.example/pixel"),
                        true))
                .isInstanceOf(InvalidMediaReferenceException.class);
        assertThatThrownBy(() -> service.replaceReferences(
                        "owner-2", MediaTargetType.LISTING, "listing-1", List.of(KEY_A), true))
                .isInstanceOf(InvalidMediaReferenceException.class);
        assertThatThrownBy(() -> service.replaceReferences(
                        "owner-1", MediaTargetType.LISTING, "listing-1", List.of(KEY_A, KEY_A), true))
                .isInstanceOf(InvalidMediaReferenceException.class);

        service.replaceReferences("owner-1", MediaTargetType.LISTING, "listing-1", List.of(KEY_A), true);
        assertThatThrownBy(() -> service.replaceReferences(
                        "owner-1", MediaTargetType.LISTING, "listing-2", List.of(KEY_A), true))
                .isInstanceOf(InvalidMediaReferenceException.class);
    }

    @Test
    void replaceAndRevoke_makeRemovedObjectsImmediatelyUnreadableAndEnqueueOnce() {
        service.registerStaged("owner-1", KEY_A, "image/jpeg", 12);
        service.registerStaged("owner-1", KEY_B, "image/png", 13);
        service.replaceReferences("owner-1", MediaTargetType.COMMUNITY_POST, "post-1", List.of(KEY_A, KEY_B), true);

        service.replaceReferences("owner-1", MediaTargetType.COMMUNITY_POST, "post-1", List.of(KEY_B), true);
        service.revokeTarget(MediaTargetType.COMMUNITY_POST, "post-1");
        service.revokeTarget(MediaTargetType.COMMUNITY_POST, "post-1");

        assertThat(assets.byKey.get(KEY_A).status()).isEqualTo(MediaAssetStatus.REVOKED);
        assertThat(assets.byKey.get(KEY_B).status()).isEqualTo(MediaAssetStatus.REVOKED);
        assertThatThrownBy(() -> service.requireReadable(KEY_A, Optional.of("owner-1")))
                .isInstanceOf(ImageNotFoundException.class);
        assertThat(deletions.byKey).containsOnlyKeys(KEY_A, KEY_B);
    }

    @Test
    void staleStage_isRevokedAndQueuedByTtlMaintenance() {
        service.registerStaged("owner-1", KEY_A, "image/jpeg", 12);
        MediaAssetLifecycleService later = serviceAt(NOW.plus(Duration.ofHours(25)));

        assertThat(later.revokeExpiredStages()).isEqualTo(1);
        assertThat(assets.byKey.get(KEY_A).status()).isEqualTo(MediaAssetStatus.REVOKED);
        assertThat(deletions.byKey).containsKey(KEY_A);
    }

    @Test
    void draftReference_staysOwnerOnlyUntilTargetBecomesPublic() {
        service.registerStaged("owner-1", KEY_A, "image/jpeg", 12);
        service.replaceReferences("owner-1", MediaTargetType.COMMUNITY_POST, "post-1", List.of(KEY_A), false);

        assertThat(assets.byKey.get(KEY_A).status()).isEqualTo(MediaAssetStatus.PRIVATE);
        assertThatCode(() -> service.requireReadable(KEY_A, Optional.of("owner-1")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireReadable(KEY_A, Optional.empty()))
                .isInstanceOf(ImageNotFoundException.class);

        MediaAssetLifecycleService afterStageTtl = serviceAt(NOW.plus(Duration.ofHours(25)));
        assertThatCode(() -> afterStageTtl.requireReadable(KEY_A, Optional.of("owner-1")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> afterStageTtl.requireReadable(KEY_A, Optional.of("owner-2")))
                .isInstanceOf(ImageNotFoundException.class);

        service.setTargetVisibility(MediaTargetType.COMMUNITY_POST, "post-1", true);
        assertThatCode(() -> service.requireReadable(KEY_A, Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void requeueDeletion_reopensCompletedJournalForSameKey() {
        deletions.byKey.put(KEY_A, MediaDeletionTask.pending(KEY_A, NOW).complete(NOW));

        service.requeueDeletion(KEY_A);

        assertThat(deletions.byKey.get(KEY_A).status()).isEqualTo(MediaDeletionTask.Status.PENDING);
        assertThat(deletions.byKey.get(KEY_A).attempts()).isZero();
        assertThat(deletions.byKey).hasSize(1);
    }

    private MediaAssetLifecycleService serviceAt(Instant instant) {
        MediaLifecycleProperties properties = new MediaLifecycleProperties(
                Duration.ofHours(24), Duration.ofMinutes(1), 100, 8, Duration.ofSeconds(30), "");
        return new MediaAssetLifecycleService(assets, deletions, properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static final class InMemoryAssets implements MediaAssetRepositoryPort {
        private final Map<String, MediaAsset> byKey = new LinkedHashMap<>();

        @Override
        public void insert(MediaAsset asset) {
            if (byKey.putIfAbsent(asset.key(), asset) != null) {
                throw new IllegalStateException("duplicate key");
            }
        }

        @Override
        public Optional<MediaAsset> findByKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }

        @Override
        public List<MediaAsset> findByKeys(Collection<String> keys) {
            return keys.stream()
                    .map(byKey::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public List<MediaAsset> findByTarget(MediaTargetType targetType, String targetId) {
            return byKey.values().stream()
                    .filter(asset -> asset.targetType() == targetType && targetId.equals(asset.targetId()))
                    .toList();
        }

        @Override
        public void saveAll(Collection<MediaAsset> changed) {
            changed.forEach(asset -> byKey.put(asset.key(), asset));
        }

        @Override
        public List<MediaAsset> findExpiredStaged(Instant now, int limit) {
            return byKey.values().stream()
                    .filter(asset -> asset.status() == MediaAssetStatus.STAGED)
                    .filter(asset -> !asset.stagedExpiresAt().isAfter(now))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class InMemoryDeletions implements MediaDeletionOutboxPort {
        private final Map<String, MediaDeletionTask> byKey = new LinkedHashMap<>();

        @Override
        public void enqueueIfAbsent(MediaDeletionTask task) {
            byKey.putIfAbsent(task.mediaKey(), task);
        }

        @Override
        public void requeue(MediaDeletionTask task) {
            byKey.put(task.mediaKey(), task);
        }

        @Override
        public List<MediaDeletionTask> findDue(Instant now, int limit) {
            return new ArrayList<>(byKey.values()).stream().limit(limit).toList();
        }

        @Override
        public List<MediaDeletionTask> findCompletedSince(Instant since) {
            return List.of();
        }

        @Override
        public void save(MediaDeletionTask task) {
            byKey.put(task.mediaKey(), task);
        }
    }
}
