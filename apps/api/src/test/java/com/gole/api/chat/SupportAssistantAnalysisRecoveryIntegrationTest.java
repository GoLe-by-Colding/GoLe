package com.gole.api.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.chat.adapter.out.persistence.SupportAssistantAnalysisMongoRepository;
import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Priority;
import com.gole.api.chat.domain.model.SupportCategory;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SupportAssistantAnalysisRecoveryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:02:03Z");

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
    SupportAssistantAnalysisRepositoryPort analyses;

    @Autowired
    SupportAssistantAnalysisMongoRepository documents;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        documents.deleteAll();
        mongo.getDb().getCollection("support_tickets").deleteMany(new Document());
        mongo.getDb()
                .getCollection("support_tickets")
                .insertOne(new Document("_id", "room-1").append("status", "RESOLVED"));
    }

    @Test
    void pendingRetryAndCompletionAreAtomicAndIdempotent() {
        assertThat(analyses.enqueue("room-1", NOW)).isTrue();
        assertThat(analyses.enqueue("room-1", NOW.plusSeconds(1))).isFalse();

        var first = analyses.tryClaim("room-1", NOW, NOW.plusSeconds(30), 5).orElseThrow();
        assertThat(first.attempt()).isEqualTo(1);
        assertThat(analyses.tryClaim("room-1", NOW.plusSeconds(1), NOW.plusSeconds(31), 5))
                .isEmpty();

        analyses.retry("room-1", first.leaseToken(), NOW.plusSeconds(2), NOW.plusSeconds(7));
        assertThat(analyses.findRecoverableRoomIds(NOW.plusSeconds(6), 5, 50)).isEmpty();
        assertThat(analyses.findRecoverableRoomIds(NOW.plusSeconds(7), 5, 50)).containsExactly("room-1");

        var second = analyses.tryClaim("room-1", NOW.plusSeconds(7), NOW.plusSeconds(37), 5)
                .orElseThrow();
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(second.leaseToken()).isNotEqualTo(first.leaseToken());

        analyses.complete("room-1", first.leaseToken(), result("stale"), NOW.plusSeconds(8));
        assertThat(analyses.findCompletedByRoomId("room-1")).isEmpty();

        Analysis completed = result("최종 초안");
        analyses.complete("room-1", second.leaseToken(), completed, NOW.plusSeconds(9));
        assertThat(analyses.findCompletedByRoomId("room-1"))
                .contains(new SupportAssistantAnalysisRepositoryPort.StoredAnalysis(
                        "room-1", completed, NOW.plusSeconds(9)));
        assertThat(analyses.findRecoverableRoomIds(NOW.plusSeconds(60), 5, 50)).isEmpty();
    }

    @Test
    void expiredProcessingLeaseIsRecoveredAfterProcessRestart() {
        analyses.enqueue("room-1", NOW);
        var abandoned = analyses.tryClaim("room-1", NOW, NOW.plusSeconds(30), 5).orElseThrow();

        assertThat(analyses.findRecoverableRoomIds(NOW.plusSeconds(29), 5, 50)).isEmpty();
        assertThat(analyses.findRecoverableRoomIds(NOW.plusSeconds(30), 5, 50)).containsExactly("room-1");

        var recovered = analyses.tryClaim("room-1", NOW.plusSeconds(30), NOW.plusSeconds(60), 5)
                .orElseThrow();
        assertThat(recovered.attempt()).isEqualTo(2);
        assertThat(recovered.leaseToken()).isNotEqualTo(abandoned.leaseToken());
    }

    private static Analysis result(String draft) {
        return new Analysis(
                SupportCategory.PRODUCT_FEEDBACK,
                Priority.NORMAL,
                "문의 요약",
                draft,
                List.of("MANUAL_REVIEW"),
                true,
                false,
                "rules-v1");
    }
}
