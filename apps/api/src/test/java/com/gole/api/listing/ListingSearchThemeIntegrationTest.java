package com.gole.api.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.catalog.application.port.out.CatalogAdminPort;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.gole.api.listing.application.port.in.SearchListingsUseCase;
import com.gole.api.listing.application.port.out.ListingRepositoryPort;
import com.gole.api.listing.application.query.ListingSearchQuery;
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

/** 한국어 테마 이름으로 찾으면, 관심 테마를 달지 않은 매물도 그 테마의 카탈로그 세트로 찾힌다(catalog → listing 연동). */
@SpringBootTest
@Testcontainers
class ListingSearchThemeIntegrationTest {

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
    SearchListingsUseCase search;

    @Autowired
    ListingRepositoryPort listings;

    @Autowired
    CatalogAdminPort catalog;

    @Autowired
    MongoTemplate mongo;

    @BeforeEach
    void seed() {
        mongo.getDb().getCollection("listings").deleteMany(new Document());
        mongo.getDb().getCollection("lego_sets").deleteMany(new Document());
        catalog.save(set("75192", "밀레니엄 팰컨 UCS", "Star Wars"), false);
        catalog.save(set("75313", "AT-AT UCS", "Star Wars"), false);
        catalog.save(set("76178", "데일리 뷰글", "Marvel Super Heroes"), false);
        catalog.save(set("10307", "에펠탑", "Icons"), false);

        listings.save(listing("falcon", "밀레니엄 팰컨 미개봉", "75192", ListingCategory.SET, null, 0));
        listings.save(listing("atat-variant", "AT-AT 조립완성", "75313-1", ListingCategory.SET, null, 1));
        listings.save(listing("vader-minifig", "다스베이더 미니피규어", "75192", ListingCategory.MINIFIG, null, 2));
        listings.save(listing("tagged-eiffel", "에펠탑 새상품", "10307", ListingCategory.SET, InterestTag.STAR_WARS, 3));
        listings.save(listing("eiffel", "에펠탑 박스 없음", "10307", ListingCategory.SET, null, 4));
        listings.save(listing("bugle", "데일리 뷰글", "76178", ListingCategory.SET, null, 5));
        listings.save(listing("no-set", "스타워즈 부품 벌크", null, ListingCategory.PARTS, null, 6));
    }

    @Test
    void koreanThemeNameFindsUntaggedListingsOfThatThemesCatalogSets() {
        // 75192·75313(변형 번호 포함)과 그 세트의 미니피규어, 판매자가 테마를 단 매물, 제목에 테마 이름을 쓴 매물.
        assertThat(ids("스타워즈"))
                .containsExactlyInAnyOrder("falcon", "atat-variant", "vader-minifig", "tagged-eiffel", "no-set");
        // 영어 테마 이름도 같은 테마다. 다만 한국어 제목("스타워즈 부품 벌크")은 영어 글자 검색에 걸리지 않는다.
        assertThat(ids("Star Wars"))
                .containsExactlyInAnyOrder("falcon", "atat-variant", "vader-minifig", "tagged-eiffel");
    }

    @Test
    void themeWordsMatchPartOfACatalogThemeName() {
        // 관심 테마 "슈퍼히어로"(super-heroes)는 카탈로그의 "Marvel Super Heroes"와 맞는다.
        assertThat(ids("슈퍼히어로")).containsExactly("bugle");
        assertThat(ids("아이콘")).containsExactlyInAnyOrder("tagged-eiffel", "eiffel");
    }

    @Test
    void partOfAThemeNameIsStillOnlyATextSearch() {
        assertThat(ids("스타")).containsExactly("no-set");
    }

    private List<String> ids(String text) {
        return search.search(new ListingSearchQuery(text, null, null, null, null)).stream()
                .map(Listing::getId)
                .toList();
    }

    private static LegoSet set(String setNumber, String name, String theme) {
        return new LegoSet(setNumber, name, theme, 1000, 2020, RetirementStatus.ACTIVE, null);
    }

    private static Listing listing(
            String id, String title, String setNumber, ListingCategory category, InterestTag interestTag, int minutes) {
        return Listing.create(
                id,
                "seller-1",
                title,
                "",
                Money.won(100_000),
                ItemCondition.NEW_SEALED,
                ConditionDisclosure.basic(),
                List.of("listing/photo.jpg"),
                setNumber,
                category,
                interestTag,
                T0.plusSeconds(minutes * 60L));
    }
}
