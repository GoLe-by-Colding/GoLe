package com.gole.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.domain.model.ConditionDisclosure;
import com.gole.api.listing.domain.model.InterestTag;
import com.gole.api.listing.domain.model.ItemCondition;
import com.gole.api.listing.domain.model.Listing;
import com.gole.api.listing.domain.model.ListingCategory;
import com.gole.api.listing.domain.model.Money;
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
class ListingInterestTagPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

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
    ListingRepositoryPort listings;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection("listings").deleteMany(new Document());
    }

    @Test
    void legacyDocumentWithoutInterestTagLoadsAsNull() {
        mongo.getDb().getCollection("listings").insertOne(legacyDocument("legacy-listing"));

        assertThat(listings.findById("legacy-listing").orElseThrow().getInterestTag())
                .isNull();
    }

    @Test
    void interestTagRoundTripsAsCatalogKey() {
        Listing listing = Listing.create(
                "tagged-listing",
                "seller-1",
                "테크닉 매물",
                "설명",
                Money.won(30_000),
                ItemCondition.USED_GOOD,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                null,
                ListingCategory.SET,
                InterestTag.TECHNIC,
                NOW);

        listings.save(listing);

        assertThat(listings.findById("tagged-listing").orElseThrow().getInterestTag())
                .isEqualTo(InterestTag.TECHNIC);
        assertThat(mongo.getCollection("listings")
                        .find(new Document("_id", "tagged-listing"))
                        .first()
                        .getString("interestTag"))
                .isEqualTo("technic");
    }

    private static Document legacyDocument(String id) {
        return new Document("_id", id)
                .append("sellerId", "seller-1")
                .append("title", "레거시 매물")
                .append("description", "설명")
                .append("priceAmount", 10_000L)
                .append("priceCurrency", "KRW")
                .append("condition", "USED_GOOD")
                .append("photoUrls", List.of("listing/photo.jpg"))
                .append("category", "SET")
                .append("status", "ACTIVE")
                .append("createdAt", java.util.Date.from(NOW));
    }
}
