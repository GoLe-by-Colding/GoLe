package com.gole.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.adapter.out.persistence.AccountDeletionRequestMongoRepository;
import com.gole.api.account.adapter.out.persistence.MongoAccountDeletionAdapter;
import com.gole.api.account.application.port.out.AccountDeletionRepositoryPort;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 replica-set Mongo에서 연계 파기, fail-closed, 전체 rollback을 검증한다. */
@Testcontainers
class AccountDeletionPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final String ACCOUNT_ID = "account-delete-target";
    private static final String EMAIL = "delete-me@gole.test";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static PlatformTransactionManager transactionManager;
    static AccountDeletionRepositoryPort deletions;
    static AccountDeletionRequestMongoRepository deletionDocuments;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        var factory = new SimpleMongoClientDatabaseFactory(client, "gole_account_deletion_test");
        mongo = new MongoTemplate(factory);
        transactionManager = new MongoTransactionManager(factory);
        deletionDocuments =
                new MongoRepositoryFactory(mongo).getRepository(AccountDeletionRequestMongoRepository.class);
        deletions = new MongoAccountDeletionAdapter(deletionDocuments, mongo);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void clean() {
        for (String collection : List.of(
                "account_deletion_requests",
                "accounts",
                "policy_acceptances",
                "third_party_provision_consent_events",
                "notifications",
                "wishlist_entries",
                "collection_items",
                "follows",
                "chat_read_cursors",
                "chat_blocks",
                "chat_messages",
                "chat_rooms",
                "social_chat_rooms",
                "reviews",
                "reports",
                "chat_report_snapshots",
                "listings",
                "posts",
                "comments",
                "listing_comments",
                "media_assets",
                "admin_actions",
                "orders",
                "shipments",
                "settlements",
                "support_tickets")) {
            mongo.getDb().getCollection(collection).deleteMany(new Document());
        }
    }

    @Test
    void transactionPurgesPersonalStateAnonymizesSharedRecordsAndLeavesOnlyNonLinkableReceipt() {
        AccountDeletionRequest request = seedReadyRequest("request-success");
        insertPersonalAndSharedRecords();

        AccountDeletionRequest completed = transaction()
                .execute(ignored -> deletions.complete(
                        request.getId(), ACCOUNT_ID, "admin-1", "completion-key-hash", "completion-fingerprint", NOW));

        assertThat(completed.getStatus()).isEqualTo(AccountDeletionStatus.COMPLETED);
        assertThat(completed.getAccountId()).isNull();
        assertThat(accountExists()).isFalse();
        for (String collection : List.of(
                "notifications",
                "wishlist_entries",
                "collection_items",
                "follows",
                "chat_messages",
                "policy_acceptances",
                "third_party_provision_consent_events")) {
            assertThat(count(collection)).as(collection).isZero();
        }

        Document receipt = document("account_deletion_requests", request.getId());
        assertThat(receipt).isNotNull();
        assertThat(receipt.get("accountId")).isNull();
        assertThat(receipt.toJson()).doesNotContain(ACCOUNT_ID, EMAIL, "민감한 관리자 사유");
        assertThat(receipt.getString("requestIdempotencyKeyHash")).isEqualTo("request-key-hash");
        assertThat(receipt.get("deletionCounts", Document.class).getLong("accounts"))
                .isEqualTo(1L);

        Document audit = document("admin_actions", "audit-1");
        assertThat(audit.getString("targetId")).isEqualTo(request.getId());
        assertThat(audit.containsKey("reason")).isFalse();
        Document review = document("reviews", "review-1");
        assertThat(review.getString("reviewerId")).startsWith("withdrawn-");
        assertThat(review.getString("content")).isEmpty();

        // 완료 주문은 보존 가능 기록으로 별도 컬렉션에 남고, 삭제된 계정 핵심정보와의 조회 연결은 끊긴다.
        Document retainedOrder = document("orders", "order-done");
        assertThat(retainedOrder.getString("buyerId")).isEqualTo(ACCOUNT_ID);
        assertThat(retainedOrder.getString("buyerPhone")).isEqualTo("01012345678");
        Document retainedShipment = document("shipments", "shipment-done");
        assertThat(retainedShipment.getString("sellerId")).isEqualTo(ACCOUNT_ID);
        assertThat(retainedShipment.getString("sellerPhone")).isEqualTo("01087654321");
        Document retainedSettlement = document("settlements", "settlement-paid");
        assertThat(retainedSettlement.getString("sellerId")).isEqualTo(ACCOUNT_ID);
        assertThat(accountExists()).isFalse();

        AccountDeletionRequest replay = transaction()
                .execute(ignored -> deletions.complete(
                        request.getId(), "", "admin-1", "completion-key-hash", "completion-fingerprint", NOW));
        assertThat(replay.getDeletionCounts()).isEqualTo(completed.getDeletionCounts());
        assertThatThrownBy(() -> transaction()
                        .executeWithoutResult(ignored -> deletions.complete(
                                request.getId(), "", "admin-1", "different-key", "different-fingerprint", NOW)))
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void activeOrderFailsClosedAndKeepsEveryRecord() {
        AccountDeletionRequest request = seedReadyRequest("request-blocked");
        insert(
                "orders",
                new Document("_id", "order-active")
                        .append("buyerId", ACCOUNT_ID)
                        .append("sellerId", "seller-1")
                        .append("status", "DISPUTED"));
        insert("notifications", new Document("_id", "notification-1").append("recipientId", ACCOUNT_ID));

        AccountDeletionRequest result = transaction()
                .execute(ignored -> deletions.complete(
                        request.getId(), ACCOUNT_ID, "admin-1", "completion-key-hash", "completion-fingerprint", NOW));

        assertThat(result.getStatus()).isEqualTo(AccountDeletionStatus.BLOCKED);
        assertThat(result.getBlockers()).containsExactly(AccountDeletionBlocker.ACTIVE_ORDER);
        assertThat(accountExists()).isTrue();
        assertThat(count("notifications")).isEqualTo(1);
        assertThat(deletionDocuments.findById(request.getId()).orElseThrow().getAccountId())
                .isEqualTo(ACCOUNT_ID);
    }

    @Test
    void outerRollbackRestoresAccountEveryLinkedCollectionAndReceiptState() {
        AccountDeletionRequest request = seedReadyRequest("request-rollback");
        insert("notifications", new Document("_id", "notification-rollback").append("recipientId", ACCOUNT_ID));

        assertThatThrownBy(() -> transaction().executeWithoutResult(ignored -> {
                    deletions.complete(
                            request.getId(),
                            ACCOUNT_ID,
                            "admin-1",
                            "completion-key-hash",
                            "completion-fingerprint",
                            NOW);
                    throw new IllegalStateException("force rollback");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(accountExists()).isTrue();
        assertThat(count("notifications")).isEqualTo(1);
        Document requestAfter = document("account_deletion_requests", request.getId());
        assertThat(requestAfter.getString("accountId")).isEqualTo(ACCOUNT_ID);
        assertThat(requestAfter.getString("status")).isEqualTo("READY");
        assertThat(requestAfter.get("completedAt")).isNull();
    }

    @Test
    void explicitRetentionHoldBlocksOtherwiseCleanDeletion() {
        AccountDeletionRequest request = seedReadyRequest("request-held");
        request.placeHold(
                com.gole.api.account.domain.model.AccountDeletionHoldReason.LEGAL_OBLIGATION,
                "admin-1",
                NOW.minusSeconds(30));
        request = deletions.save(request);

        AccountDeletionRequest result = transaction()
                .execute(ignored -> deletions.complete(
                        "request-held", ACCOUNT_ID, "admin-1", "completion-key-hash", "completion-fingerprint", NOW));

        assertThat(result.getStatus()).isEqualTo(AccountDeletionStatus.BLOCKED);
        assertThat(result.getBlockers()).containsExactly(AccountDeletionBlocker.EXPLICIT_RETENTION_HOLD);
        assertThat(accountExists()).isTrue();
    }

    private AccountDeletionRequest seedReadyRequest(String requestId) {
        insert(
                "accounts",
                new Document("_id", ACCOUNT_ID)
                        .append("email", EMAIL)
                        .append("passwordHash", "password-hash")
                        .append("status", "SUSPENDED")
                        .append("role", "USER")
                        .append("suspendedReason", AccountDeletionRequest.suspensionReason(requestId)));
        return deletions.save(AccountDeletionRequest.requested(
                requestId, ACCOUNT_ID, "request-key-hash", "request-fingerprint", List.of(), NOW.minusSeconds(60)));
    }

    private void insertPersonalAndSharedRecords() {
        insert("notifications", new Document("_id", "notification-1").append("recipientId", ACCOUNT_ID));
        insert("wishlist_entries", new Document("_id", "wishlist-1").append("userId", ACCOUNT_ID));
        insert("collection_items", new Document("_id", "collection-1").append("userId", ACCOUNT_ID));
        insert(
                "follows",
                new Document("_id", "follow-1").append("userId", ACCOUNT_ID).append("sellerId", "seller-1"));
        insert(
                "chat_messages",
                new Document("_id", "message-1")
                        .append("roomId", "room-1")
                        .append("senderId", ACCOUNT_ID)
                        .append("content", "민감한 메시지"));
        insert(
                "reviews",
                new Document("_id", "review-1")
                        .append("orderId", "order-done")
                        .append("reviewerId", ACCOUNT_ID)
                        .append("revieweeId", "seller-1")
                        .append("content", "민감한 후기")
                        .append("rating", 5));
        insert(
                "listings",
                new Document("_id", "listing-deleted")
                        .append("sellerId", ACCOUNT_ID)
                        .append("status", "DELETED")
                        .append("title", "민감한 제목")
                        .append("description", "민감한 설명"));
        insert("policy_acceptances", new Document("_id", "policy-1").append("accountId", ACCOUNT_ID));
        insert(
                "third_party_provision_consent_events",
                new Document("_id", "consent-1").append("accountId", ACCOUNT_ID));
        insert(
                "admin_actions",
                new Document("_id", "audit-1")
                        .append("targetType", "ACCOUNT")
                        .append("targetId", ACCOUNT_ID)
                        .append("reason", "민감한 관리자 사유"));
        insert(
                "orders",
                new Document("_id", "order-done")
                        .append("buyerId", ACCOUNT_ID)
                        .append("sellerId", "seller-1")
                        .append("buyerPhone", "01012345678")
                        .append("status", "COMPLETED"));
        insert(
                "shipments",
                new Document("_id", "shipment-done")
                        .append("orderId", "seller-order-done")
                        .append("sellerId", ACCOUNT_ID)
                        .append("buyerId", "buyer-1")
                        .append("sellerPhone", "01087654321")
                        .append("status", "DELIVERED"));
        insert(
                "settlements",
                new Document("_id", "settlement-paid")
                        .append("orderId", "seller-order-done")
                        .append("sellerId", ACCOUNT_ID)
                        .append("status", "PAID"));
    }

    private void insert(String collection, Document document) {
        mongo.getDb().getCollection(collection).insertOne(document);
    }

    private long count(String collection) {
        return mongo.getDb().getCollection(collection).countDocuments();
    }

    private boolean accountExists() {
        return document("accounts", ACCOUNT_ID) != null;
    }

    private Document document(String collection, String id) {
        return mongo.getDb()
                .getCollection(collection)
                .find(new Document("_id", id))
                .first();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
