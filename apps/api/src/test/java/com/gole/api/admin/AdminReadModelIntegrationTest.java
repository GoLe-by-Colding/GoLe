package com.gole.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.adapter.out.persistence.MongoAccountCountAdapter;
import com.gole.api.account.application.service.AccountCountService;
import com.gole.api.admin.adapter.out.readmodel.CrossContextAdminReadModelAdapter;
import com.gole.api.admin.domain.model.AdminListingRow;
import com.gole.api.admin.domain.model.AdminOrderRow;
import com.gole.api.admin.domain.model.AdminPostRow;
import com.gole.api.catalog.adapter.out.persistence.MongoLegoSetCountAdapter;
import com.gole.api.catalog.application.service.LegoSetCountService;
import com.gole.api.community.adapter.out.persistence.MongoPostMonitoringAdapter;
import com.gole.api.community.application.service.PostMonitoringService;
import com.gole.api.listing.adapter.out.persistence.MongoListingMonitoringAdapter;
import com.gole.api.listing.application.service.ListingMonitoringService;
import com.gole.api.order.adapter.out.persistence.MongoOrderMonitoringAdapter;
import com.gole.api.order.application.service.OrderMonitoringService;
import com.gole.api.pricing.adapter.out.persistence.MongoPriceTransactionCountAdapter;
import com.gole.api.pricing.application.service.PriceTransactionCountService;
import com.gole.api.review.adapter.out.persistence.MongoReviewCountAdapter;
import com.gole.api.review.application.service.ReviewCountService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영 화면 읽기 모델이 다른 컨텍스트 컬렉션을 직접 읽지 않고 소유 컨텍스트의 조회 유스케이스를 거쳐도 같은 값을 내는지
 * 실제 Mongo 에서 확인한다.
 */
@Testcontainers
class AdminReadModelIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static CrossContextAdminReadModelAdapter readModel;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "gole_admin_read_model_test");
        readModel = new CrossContextAdminReadModelAdapter(
                new OrderMonitoringService(new MongoOrderMonitoringAdapter(mongo)),
                new ListingMonitoringService(new MongoListingMonitoringAdapter(mongo)),
                new PostMonitoringService(new MongoPostMonitoringAdapter(mongo)),
                new AccountCountService(new MongoAccountCountAdapter(mongo)),
                new LegoSetCountService(new MongoLegoSetCountAdapter(mongo)),
                new ReviewCountService(new MongoReviewCountAdapter(mongo)),
                new PriceTransactionCountService(new MongoPriceTransactionCountAdapter(mongo)));
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @BeforeEach
    void seed() {
        for (String name :
                List.of("orders", "listings", "posts", "accounts", "lego_sets", "reviews", "price_transactions")) {
            mongo.getCollection(name).deleteMany(new Document());
        }
        mongo.getCollection("orders")
                .insertMany(List.of(
                        order(
                                "order-1",
                                "COMPLETED",
                                30_000,
                                "buyer-Alpha",
                                T0,
                                new Document("type", "EASY_PAY").append("provider", "KAKAOPAY")),
                        order("order-2", "COMPLETED", 20_000, "buyer-beta", T0.plusSeconds(60), null),
                        order(
                                "order-3",
                                "PAYMENT_PENDING",
                                50_000,
                                "buyer-alpha",
                                T0.plusSeconds(120),
                                new Document("provider", "KAKAOPAY"))));
        mongo.getCollection("listings")
                .insertMany(List.of(
                        listing("listing-1", "에펠탑 미개봉", "ACTIVE", T0),
                        listing("listing-2", "타이타닉", "DELETED", T0.plusSeconds(60))));
        mongo.getCollection("posts")
                .insertMany(List.of(
                        new Document("_id", "post-1")
                                .append("authorId", "user-1")
                                .append("type", "tip")
                                .append("status", "ACTIVE")
                                .append("createdAt", Date.from(T0)),
                        new Document("_id", "post-2")
                                .append("authorId", "user-2")
                                .append("content", "숨긴 글")
                                .append("type", "free")
                                .append("status", "HIDDEN")
                                .append("createdAt", Date.from(T0.plusSeconds(60)))));
        mongo.getCollection("accounts").insertMany(List.of(new Document("_id", "a-1"), new Document("_id", "a-2")));
        mongo.getCollection("lego_sets").insertOne(new Document("_id", "10307"));
        mongo.getCollection("reviews")
                .insertMany(
                        List.of(new Document("_id", "r-1"), new Document("_id", "r-2"), new Document("_id", "r-3")));
        mongo.getCollection("price_transactions").insertOne(new Document("_id", "p-1"));
    }

    @Test
    @DisplayName("주문 상태별 건수와 완료 거래액을 order 컨텍스트가 집계한다")
    void orderStats_countByStatusAndCompletedGmv() {
        var stats = readModel.orderStats();

        assertThat(stats.countByStatus()).containsEntry("COMPLETED", 2L).containsEntry("PAYMENT_PENDING", 1L);
        assertThat(stats.completedGmv()).isEqualTo(50_000);
    }

    @Test
    @DisplayName("최근 주문은 최신순이고 검색은 대소문자를 가리지 않으며, 분류 없는 결제수단은 비운다")
    void recentOrders_searchAndPaymentMethod() {
        List<AdminOrderRow> rows = readModel.recentOrders(null, "ALPHA", 10);

        assertThat(rows).extracting(AdminOrderRow::id).containsExactly("order-3", "order-1");
        assertThat(rows.get(0).paymentMethod()).isNull();
        assertThat(rows.get(1).paymentMethod().type()).isEqualTo("EASY_PAY");
        assertThat(rows.get(1).paymentMethod().provider()).isEqualTo("KAKAOPAY");
        assertThat(readModel.recentOrders("COMPLETED", null, 10)).hasSize(2);
    }

    @Test
    @DisplayName("매물 모니터링은 삭제된 매물까지 보고, 활성 매물 수는 ACTIVE 만 센다")
    void listings_includeDeletedAndCountActive() {
        List<AdminListingRow> rows = readModel.recentListings(null, null, 10);

        assertThat(rows).extracting(AdminListingRow::id).containsExactly("listing-2", "listing-1");
        assertThat(rows.get(0).status()).isEqualTo("DELETED");
        assertThat(readModel.recentListings(null, "에펠", 10))
                .extracting(AdminListingRow::id)
                .containsExactly("listing-1");
        assertThat(readModel.activeListingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 모니터링은 숨긴 글까지 보고, 내용이 없으면 빈 문자열로 낸다")
    void posts_includeHiddenAndBlankContent() {
        List<AdminPostRow> rows = readModel.recentPosts(null, null, 10);

        assertThat(rows).extracting(AdminPostRow::id).containsExactly("post-2", "post-1");
        assertThat(rows.get(1).content()).isEmpty();
        assertThat(readModel.recentPosts("HIDDEN", null, 10))
                .extracting(AdminPostRow::id)
                .containsExactly("post-2");
    }

    @Test
    @DisplayName("규모 숫자는 각 소유 컨텍스트가 센 값이다")
    void volumeCounts_fromOwningContexts() {
        var counts = readModel.volumeCounts();

        assertThat(counts.accounts()).isEqualTo(2);
        assertThat(counts.legoSets()).isEqualTo(1);
        assertThat(counts.listings()).isEqualTo(2);
        assertThat(counts.orders()).isEqualTo(3);
        assertThat(counts.posts()).isEqualTo(2);
        assertThat(counts.reviews()).isEqualTo(3);
        assertThat(counts.priceTransactions()).isEqualTo(1);
    }

    private static Document order(
            String id, String status, long amount, String buyerId, Instant createdAt, Document method) {
        Document document = new Document("_id", id)
                .append("status", status)
                .append("amount", amount)
                .append("buyerId", buyerId)
                .append("sellerId", "seller-1")
                .append("catalogSetNumber", "10307")
                .append("createdAt", Date.from(createdAt));
        if (method != null) {
            document.append("paymentMethod", method);
        }
        return document;
    }

    private static Document listing(String id, String title, String status, Instant createdAt) {
        return new Document("_id", id)
                .append("title", title)
                .append("sellerId", "seller-1")
                .append("priceAmount", 890_000L)
                .append("status", status)
                .append("category", "SET")
                .append("createdAt", Date.from(createdAt));
    }
}
