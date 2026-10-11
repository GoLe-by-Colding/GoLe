package com.gole.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.application.port.out.ListingCommentRepositoryPort;
import com.gole.api.listing.domain.model.ListingComment;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 매물 문의 댓글 저장소. 삭제 표시는 빼고 오래된 순으로 상한까지만 읽는다. */
@SpringBootTest
@Testcontainers
class ListingCommentPersistenceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-10T00:00:00Z");

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
        registry.add("gole.review.seed-on-empty", () -> "false");
        registry.add("gole.media.seed-on-startup", () -> "false");
        registry.add("gole.support-notification-outbox.processing-enabled", () -> "false");
    }

    @Autowired
    ListingCommentRepositoryPort comments;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clear() {
        mongo.getDb().getCollection("listing_comments").deleteMany(new Document());
    }

    @Test
    @DisplayName("삭제 표시된 댓글과 다른 매물 댓글은 빼고 오래된 순으로 읽는다")
    void findActiveByListingId_excludesDeletedAndSortsOldestFirst() {
        comments.save(new ListingComment("c-3", "listing-1", "buyer-3", "세 번째", false, T0.plusSeconds(3)));
        comments.save(new ListingComment("c-1", "listing-1", "buyer-1", "첫 번째", false, T0.plusSeconds(1)));
        comments.save(new ListingComment("c-2", "listing-1", "buyer-2", "지운 글", true, T0.plusSeconds(2)));
        comments.save(new ListingComment("c-x", "listing-2", "buyer-1", "다른 매물", false, T0));

        assertThat(comments.findActiveByListingId("listing-1", 200))
                .extracting(ListingComment::id)
                .containsExactly("c-1", "c-3");
    }

    @Test
    @DisplayName("상한을 넘으면 오래된 것부터 상한까지만 읽는다")
    void findActiveByListingId_appliesLimit() {
        for (int i = 0; i < 5; i++) {
            comments.save(ListingComment.post("c-" + i, "listing-1", "buyer", "글 " + i, T0.plusSeconds(i)));
        }

        assertThat(comments.findActiveByListingId("listing-1", 3))
                .extracting(ListingComment::id)
                .containsExactly("c-0", "c-1", "c-2");
    }
}
