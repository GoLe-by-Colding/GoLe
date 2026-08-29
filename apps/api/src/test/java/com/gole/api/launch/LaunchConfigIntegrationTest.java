package com.gole.api.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.launch.adapter.out.persistence.LaunchConfigDocument;
import com.gole.api.launch.adapter.out.persistence.LaunchConfigPersistenceAdapter;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.ChangeStageCommand;
import com.gole.api.launch.application.port.in.ManageLaunchConfigUseCase.SetFeatureOverrideCommand;
import com.gole.api.launch.application.port.out.LaunchConfigHistoryPort;
import com.gole.api.launch.application.port.out.LaunchConfigRepositoryPort;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchConfigChange;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import com.gole.api.launch.domain.model.TradeMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 공개 설정이 실제 MongoDB 에 남고 다시 읽히는지 검증한다.
 *
 * <p>단위 테스트는 포트를 목으로 대신하므로 "저장은 됐는데 읽을 때 매핑이 어긋나는" 사고를
 * 잡지 못한다. 특히 override 는 enum 키를 문자열로 저장했다가 되돌리는 구간이라 왕복 검증이 필요하다.
 */
@SpringBootTest
@Testcontainers
@Import(LaunchConfigIntegrationTest.TransactionProbeConfig.class)
class LaunchConfigIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("gole.catalog.seed-on-empty", () -> "false");
        registry.add("gole.listing.seed-on-empty", () -> "false");
        registry.add("gole.pricing.seed-on-empty", () -> "false");
        registry.add("gole.community.seed-on-empty", () -> "false");
        registry.add("gole.report.seed-on-empty", () -> "false");
        registry.add("gole.media.seed-on-startup", () -> "false");
    }

    @Autowired
    GetLaunchConfigUseCase getLaunchConfig;

    @Autowired
    ManageLaunchConfigUseCase manageLaunchConfig;

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    LaunchConfigRepositoryPort launchConfigRepository;

    @Autowired
    TransactionProbeHistoryPort transactionProbeHistory;

    @BeforeEach
    void setUp() {
        transactionProbeHistory.reset();
        mongoTemplate.getDb().getCollection("launch_config").deleteMany(new org.bson.Document());
        mongoTemplate.getDb().getCollection("launch_config_changes").deleteMany(new org.bson.Document());
    }

    @Test
    @DisplayName("설정이 없으면 당근형 직거래 단계로 시작한다")
    void unsetConfigStartsFailClosed() {
        LaunchConfig current = getLaunchConfig.current();

        assertThat(current.stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(current.updatedAt()).isNull();
    }

    @Test
    @DisplayName("단계 변경이 DB에 남고 다시 읽힌다")
    void stageChangeIsPersisted() {
        manageLaunchConfig.changeStage(
                new ChangeStageCommand(LaunchStage.PREPARING, "점검 모드", "admin-1", "admin@gole.local"));

        LaunchConfig reloaded = getLaunchConfig.current();

        assertThat(reloaded.stage()).isEqualTo(LaunchStage.PREPARING);
        assertThat(reloaded.tradeMode()).isEqualTo(TradeMode.DIRECT_CHAT);
        assertThat(reloaded.platformHandlesMoney()).isFalse();
        assertThat(reloaded.updatedAt()).isNotNull();
        assertThat(reloaded.updatedBy()).isEqualTo("admin-1");
    }

    @Test
    @DisplayName("실행 조건보다 높은 단계는 Stage 1로 영구 잠겨 환경 복구만으로 재개되지 않는다")
    void runtimeSafetyClampIsPersisted() {
        launchConfigRepository.save(new LaunchConfig(LaunchStage.FULL, Map.of(), Instant.now(), "admin-1"));

        LaunchConfig effective = getLaunchConfig.current();
        LaunchConfig persisted = launchConfigRepository.load().orElseThrow();

        assertThat(effective.stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(persisted.stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(persisted.updatedBy()).isEqualTo("system:launch-safety-clamp");
        assertThat(getLaunchConfig.current().stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(manageLaunchConfig.history(10)).singleElement().satisfies(change -> {
            assertThat(change.before()).isEqualTo("3");
            assertThat(change.after()).isEqualTo("1");
            assertThat(change.actorId()).isEqualTo("system:launch-safety-clamp");
            assertThat(change.reason()).contains("안전 잠금");
        });
    }

    @Test
    @DisplayName("안전 잠금 이력 저장이 실패하면 단계 하향도 같이 롤백된다")
    void safetyClampHistoryFailureRollsBackStageTogether() {
        launchConfigRepository.save(new LaunchConfig(LaunchStage.FULL, Map.of(), Instant.now(), "admin-1"));
        transactionProbeHistory.failAfterNextAppend();

        assertThatThrownBy(() -> getLaunchConfig.current()).isInstanceOf(TestHistoryWriteFailure.class);

        LaunchConfig persisted = launchConfigRepository.load().orElseThrow();
        assertThat(persisted.stage()).isEqualTo(LaunchStage.FULL);
        assertThat(persisted.updatedBy()).isEqualTo("admin-1");
        assertThat(transactionProbeHistory.findRecent(10)).isEmpty();
    }

    @Test
    @DisplayName("override 는 문자열로 저장됐다가 기능으로 되돌아온다")
    void featureOverrideSurvivesRoundTrip() {
        manageLaunchConfig.changeStage(
                new ChangeStageCommand(LaunchStage.PREPARING, "초기화", "admin-1", "admin@gole.local"));

        manageLaunchConfig.setFeatureOverride(
                new SetFeatureOverrideCommand(LaunchFeature.REVIEWS, true, "베타 리뷰 선공개", "admin-1", "admin@gole.local"));

        LaunchConfig reloaded = getLaunchConfig.current();
        assertThat(reloaded.overrides()).containsEntry(LaunchFeature.REVIEWS, true);
        assertThat(reloaded.isEnabled(LaunchFeature.REVIEWS)).isTrue();
        // 단계 기본이 닫힌 기능만 열렸고 나머지는 그대로다.
        assertThat(reloaded.isEnabled(LaunchFeature.PAYMENTS)).isFalse();
    }

    @Test
    @DisplayName("override 해제는 단계 기본값으로 되돌린다")
    void clearingOverrideIsPersisted() {
        manageLaunchConfig.changeStage(
                new ChangeStageCommand(LaunchStage.PREPARING, "초기화", "admin-1", "admin@gole.local"));
        manageLaunchConfig.setFeatureOverride(
                new SetFeatureOverrideCommand(LaunchFeature.REVIEWS, true, "선공개", "admin-1", "admin@gole.local"));

        manageLaunchConfig.setFeatureOverride(
                new SetFeatureOverrideCommand(LaunchFeature.REVIEWS, null, "선공개 종료", "admin-1", "admin@gole.local"));

        LaunchConfig reloaded = getLaunchConfig.current();
        assertThat(reloaded.overrides()).doesNotContainKey(LaunchFeature.REVIEWS);
        assertThat(reloaded.isEnabled(LaunchFeature.REVIEWS)).isFalse();
    }

    @Test
    @DisplayName("변경 이력이 사유와 함께 최신순으로 쌓인다")
    void historyIsAppendedWithReason() {
        manageLaunchConfig.changeStage(
                new ChangeStageCommand(LaunchStage.PREPARING, "첫 설정", "admin-1", "admin@gole.local"));
        manageLaunchConfig.changeStage(
                new ChangeStageCommand(LaunchStage.BROWSE_ONLY, "열람 오픈", "admin-2", "admin2@gole.local"));

        List<LaunchConfigChange> history = manageLaunchConfig.history(10);

        assertThat(history).hasSize(2);
        assertThat(history.getFirst().reason()).isEqualTo("열람 오픈");
        assertThat(history.getFirst().before()).isEqualTo("0");
        assertThat(history.getFirst().after()).isEqualTo("1");
        assertThat(history.getFirst().actorId()).isEqualTo("admin-2");
    }

    @Test
    @DisplayName("사유 없는 변경은 저장도 이력도 남기지 않는다")
    void rejectedChangeLeavesNoTrace() {
        assertThatThrownBy(() -> manageLaunchConfig.changeStage(
                        new ChangeStageCommand(LaunchStage.BROWSE_ONLY, "", "admin-1", "admin@gole.local")))
                .isInstanceOf(BadRequestException.class);

        assertThat(manageLaunchConfig.history(10)).isEmpty();
        assertThat(getLaunchConfig.current().updatedAt()).isNull();
    }

    @Test
    @DisplayName("같은 버전을 읽은 두 관리자의 동시 갱신은 하나만 저장한다")
    void concurrentUpdatesDoNotLoseOneAnotherSilently() throws Exception {
        launchConfigRepository.save(LaunchConfig.unset());
        LaunchConfig firstRead = launchConfigRepository.load().orElseThrow();
        LaunchConfig secondRead = launchConfigRepository.load().orElseThrow();
        assertThat(firstRead.version()).isEqualTo(secondRead.version()).isEqualTo(0L);

        LaunchConfig stageUpdate = firstRead.withStage(LaunchStage.PREPARING, Instant.now(), "admin-stage");
        LaunchConfig reviewUpdate = secondRead.withOverride(LaunchFeature.REVIEWS, true, Instant.now(), "admin-review");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> stageResult = workers.submit(() -> saveAfter(start, stageUpdate));
            Future<Throwable> reviewResult = workers.submit(() -> saveAfter(start, reviewUpdate));
            start.countDown();

            Throwable stageFailure = stageResult.get(10, TimeUnit.SECONDS);
            Throwable reviewFailure = reviewResult.get(10, TimeUnit.SECONDS);
            long successes = java.util.stream.Stream.of(stageFailure, reviewFailure)
                    .filter(Objects::isNull)
                    .count();
            Throwable rejected = java.util.stream.Stream.of(stageFailure, reviewFailure)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow();

            assertThat(successes).isEqualTo(1);
            assertThat(rejected).isInstanceOf(OptimisticLockingFailureException.class);
        } finally {
            workers.shutdownNow();
        }

        LaunchConfig persisted = launchConfigRepository.load().orElseThrow();
        assertThat(persisted.version()).isEqualTo(1L);
        assertThat(isStageUpdate(persisted) || isReviewUpdate(persisted)).isTrue();
    }

    @Test
    @DisplayName("이력 저장이 실패하면 설정 변경과 이력이 같이 롤백된다")
    void historyFailureRollsBackConfigAndHistoryTogether() {
        launchConfigRepository.save(LaunchConfig.unset());
        transactionProbeHistory.failAfterNextAppend();

        assertThatThrownBy(() -> manageLaunchConfig.changeStage(
                        new ChangeStageCommand(LaunchStage.PREPARING, "트랜잭션 검증", "admin-1", "admin@gole.local")))
                .isInstanceOf(TestHistoryWriteFailure.class);

        LaunchConfig persisted = launchConfigRepository.load().orElseThrow();
        assertThat(persisted.stage()).isEqualTo(LaunchStage.BROWSE_ONLY);
        assertThat(persisted.version()).isEqualTo(0L);
        assertThat(transactionProbeHistory.findRecent(10)).isEmpty();
    }

    @Test
    @DisplayName("버전 필드가 없는 기존 문서는 0으로 승격하고 다음 갱신부터 낙관적 잠금을 쓴다")
    void legacyDocumentWithoutVersionIsUpgraded() {
        mongoTemplate
                .getDb()
                .getCollection("launch_config")
                .insertOne(new Document("_id", LaunchConfigDocument.SINGLETON_ID)
                        .append("stage", LaunchStage.PREPARING.level())
                        .append("overrides", new Document())
                        .append("updatedBy", "legacy-admin"));

        LaunchConfig upgraded = launchConfigRepository.load().orElseThrow();
        Document rawUpgraded = mongoTemplate
                .getDb()
                .getCollection("launch_config")
                .find(new Document("_id", LaunchConfigDocument.SINGLETON_ID))
                .first();

        assertThat(upgraded.version()).isEqualTo(0L);
        assertThat(rawUpgraded).isNotNull();
        assertThat(rawUpgraded.get("version", Number.class).longValue()).isZero();

        launchConfigRepository.save(upgraded.withOverride(LaunchFeature.REVIEWS, true, Instant.now(), "admin-upgrade"));

        LaunchConfig savedAgain = launchConfigRepository.load().orElseThrow();
        assertThat(savedAgain.version()).isEqualTo(1L);
        assertThat(savedAgain.overrides()).containsEntry(LaunchFeature.REVIEWS, true);
    }

    private Throwable saveAfter(CountDownLatch start, LaunchConfig config) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return new AssertionError("동시 저장 시작 신호를 기다리다 시간이 초과됐습니다");
            }
            launchConfigRepository.save(config);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static boolean isStageUpdate(LaunchConfig config) {
        return config.stage() == LaunchStage.PREPARING && config.overrides().isEmpty();
    }

    private static boolean isReviewUpdate(LaunchConfig config) {
        return config.stage() == LaunchStage.BROWSE_ONLY
                && config.overrides().equals(Map.of(LaunchFeature.REVIEWS, true));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionProbeConfig {

        @Bean
        @Primary
        TransactionProbeHistoryPort transactionProbeHistory(LaunchConfigPersistenceAdapter delegate) {
            return new TransactionProbeHistoryPort(delegate);
        }
    }

    static final class TransactionProbeHistoryPort implements LaunchConfigHistoryPort {

        private final LaunchConfigHistoryPort delegate;
        private boolean failAfterAppend;

        TransactionProbeHistoryPort(LaunchConfigHistoryPort delegate) {
            this.delegate = delegate;
        }

        synchronized void failAfterNextAppend() {
            failAfterAppend = true;
        }

        synchronized void reset() {
            failAfterAppend = false;
        }

        @Override
        public synchronized void append(LaunchConfigChange change) {
            delegate.append(change);
            if (failAfterAppend) {
                failAfterAppend = false;
                throw new TestHistoryWriteFailure();
            }
        }

        @Override
        public List<LaunchConfigChange> findRecent(int limit) {
            return delegate.findRecent(limit);
        }
    }

    static final class TestHistoryWriteFailure extends RuntimeException {

        TestHistoryWriteFailure() {
            super("테스트용 이력 저장 실패");
        }
    }
}
