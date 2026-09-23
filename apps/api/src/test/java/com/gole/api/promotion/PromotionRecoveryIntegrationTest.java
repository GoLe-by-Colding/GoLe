package com.gole.api.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.promotion.adapter.out.persistence.PromotionPostDocument;
import com.gole.api.promotion.adapter.out.persistence.PromotionPostMongoRepository;
import com.gole.api.promotion.adapter.out.persistence.PromotionPostPersistenceAdapter;
import com.gole.api.promotion.domain.exception.SourceCommitAlreadyPromotedException;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
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
