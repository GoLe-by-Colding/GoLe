package com.gole.api.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.promotion.adapter.out.persistence.PromotionPostDocument;
import com.gole.api.promotion.adapter.out.persistence.PromotionPostMongoRepository;
import com.gole.api.promotion.adapter.out.persistence.PromotionPostPersistenceAdapter;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.model.CaptureDataSource;
import com.gole.api.promotion.domain.model.PromotionCapture;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionProvenance;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 Mongo 인덱스로 재제출 경쟁과 반려 초안 보존을 확인한다. */
@Testcontainers
class PromotionRecoveryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static PromotionPostMongoRepository documents;
    static PromotionPostPersistenceAdapter posts;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        var mongo = new MongoTemplate(client, "gole_promotion_recovery_test");
        documents = new MongoRepositoryFactory(mongo).getRepository(PromotionPostMongoRepository.class);
        var resolver =
                new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext());
        resolver.resolveIndexFor(PromotionPostDocument.class)
                .forEach(index -> mongo.indexOps(PromotionPostDocument.class).ensureIndex(index));
        posts = new PromotionPostPersistenceAdapter(documents);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.close();
    }

    @BeforeEach
    void clean() {
        documents.deleteAll();
    }

    @Test
    @DisplayName("반려 초안 여러 개를 보존하면서 재제출한 초안의 점유와 검토 초기화를 저장한다")
    void resubmit_restoresClaimAndClearsReview() {
        rejected("first");
        rejected("second");
        var first = posts.findById("first").orElseThrow();
        first.submitForReview(NOW.plusSeconds(5));
        posts.save(first);

        var restored = posts.findById("first").orElseThrow();
        assertThat(restored.getClaimedSourceCommitSha()).isEqualTo(SHA);
        assertThat(restored.getReviewedAt()).isNull();
        assertThat(restored.getRejectionReason()).isNull();
        assertThat(posts.findReviewTimestamps()).hasSize(1);
        assertThat(documents.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 릴리스의 두 반려 초안이 동시에 재제출되면 하나만 점유한다")
    void resubmit_concurrentClaimsHaveOneWinner() throws Exception {
        rejected("first");
        rejected("second");
        var barrier = new CyclicBarrier(2);
        List<Callable<Boolean>> attempts = List.of(() -> reclaim("first", barrier), () -> reclaim("second", barrier));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executor.invokeAll(attempts, 15, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(posts.countByStatus(PromotionPostStatus.PENDING_REVIEW)).isEqualTo(1);
        assertThat(posts.countByStatus(PromotionPostStatus.DRAFT)).isEqualTo(1);
    }

    @Test
    @DisplayName("실제 Mongo 에서 종류·설명표·출처가 저장했다 읽어도 그대로다")
    void context_roundTripsThroughMongo() {
        var context = new PromotionPostContext(
                PromotionCategory.SERVICE,
                List.of(new PromotionCapture("필터 열린 목록", "/market", "필터 클릭", CaptureDataSource.DEMO, NOW)),
                new PromotionProvenance("릴리스", "이유", "https://github.com/o/r/actions/runs/1"));
        posts.save(PromotionPost.draft(
                "ctx", PromotionChannel.THREADS, "초안", List.of("/a.png"), "author", null, NOW, context));

        assertThat(posts.findById("ctx").orElseThrow().context()).isEqualTo(context);
    }

    @Test
    @DisplayName("다음 차례와 최신 발행 시각을 실제 정렬로 고른다")
    void publishOrderQueries_useReviewAndPublishTimes() {
        approved("later", NOW.plusSeconds(20));
        approved("earlier", NOW.plusSeconds(10));
        var published = PromotionPost.draft("done", PromotionChannel.THREADS, "초안", List.of(), "author", null, NOW);
        published.submitForReview(NOW);
        published.approve("reviewer", NOW);
        published.markPublished("stub-1", NOW.plusSeconds(30));
        posts.save(published);

        assertThat(posts.findOldestApproved().orElseThrow().getId()).isEqualTo("earlier");
        assertThat(posts.findLatestPublishedAt()).contains(NOW.plusSeconds(30));
    }

    private void approved(String id, Instant reviewedAt) {
        var post = PromotionPost.draft(id, PromotionChannel.THREADS, "초안", List.of(), "author", null, NOW);
        post.submitForReview(NOW);
        post.approve("reviewer", reviewedAt);
        posts.save(post);
    }

    private boolean reclaim(String id, CyclicBarrier barrier) throws Exception {
        var post = posts.findById(id).orElseThrow();
        post.submitForReview(NOW.plusSeconds(5));
        barrier.await(10, TimeUnit.SECONDS);
        try {
            posts.save(post);
            return true;
        } catch (SourceCommitAlreadyPromotedException expected) {
            return false;
        }
    }

    private void rejected(String id) {
        var post = PromotionPost.draft(id, PromotionChannel.THREADS, "초안", List.of(), "author", SHA, NOW);
        post.submitForReview(NOW.plusSeconds(1));
        post.reject("reviewer", "다시 검토", NOW.plusSeconds(2));
        posts.save(post);
    }
}
