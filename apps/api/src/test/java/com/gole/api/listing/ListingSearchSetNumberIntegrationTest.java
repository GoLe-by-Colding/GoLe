package com.gole.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
import com.gole.api.listing.domain.model.ConditionDisclosure;
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

/** 세트 번호로 찾는 검색. 제목·설명에 번호가 없어도 카탈로그 세트 번호로 찾힌다. */
@SpringBootTest
@Testcontainers
class ListingSearchSetNumberIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-09T00:00:00Z");

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
    void seed() {
        mongo.getDb().getCollection("listings").deleteMany(new Document());
        listings.save(listing("field-only", "에펠탑 미개봉 새상품", "선물용으로 보관", "10307", 0));
        listings.save(listing("text-only", "에펠탑 10307 정품", "", null, 1));
        listings.save(listing("variant-field", "에펠탑 설명서 포함", "", "10307-1", 2));
        listings.save(listing("other-prefix", "다른 세트", "", "103070", 3));
        listings.save(listing("falcon", "밀레니엄 팰컨", "", "75192", 4));
    }

    @Test
    void setNumberFindsListingsWhoseTitleOmitsTheNumber() {
        assertThat(ids("10307")).containsExactlyInAnyOrder("field-only", "text-only", "variant-field");
        assertThat(ids("#10307")).containsExactlyInAnyOrder("field-only", "text-only", "variant-field");
        assertThat(ids("10307-1")).containsExactlyInAnyOrder("field-only", "text-only", "variant-field");
    }

    @Test
    void partialNumberAndWordsStillSearchTextOnly() {
        // 번호 일부는 세트 번호 칸과 정확히 맞춰 보지 않는다 — 다른 세트(103070)를 끌고 오지 않는다.
        assertThat(ids("1030")).containsExactly("text-only");
        assertThat(ids("에펠탑")).containsExactlyInAnyOrder("field-only", "text-only", "variant-field");
        assertThat(ids("팰컨")).containsExactly("falcon");
    }

    private List<String> ids(String text) {
        return listings.search(new ListingSearchQuery(text, null, null, null, null)).stream()
                .map(Listing::getId)
                .toList();
    }

    private static Listing listing(String id, String title, String description, String setNumber, int minutes) {
        return Listing.create(
                id,
                "seller-1",
                title,
                description,
                Money.won(100_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                setNumber,
                ListingCategory.SET,
                null,
                T0.plusSeconds(minutes * 60L));
    }
}
