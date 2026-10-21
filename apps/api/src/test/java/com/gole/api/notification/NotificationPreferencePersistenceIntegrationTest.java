package com.gole.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.adapter.out.persistence.NotificationPreferenceMongoRepository;
import com.gole.api.notification.adapter.out.persistence.NotificationPreferencePersistenceAdapter;
import com.gole.api.notification.application.service.NotificationPreferenceService;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 Mongo에서 수신 설정 저장·덮어쓰기와 팬아웃용 일괄 조회를 검증한다. */
@Testcontainers
class NotificationPreferencePersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final String COLLECTION = "notification_preferences";

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static NotificationPreferencePersistenceAdapter adapter;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "gole_notification_preference_test"));
        adapter = new NotificationPreferencePersistenceAdapter(
                new MongoRepositoryFactory(mongo).getRepository(NotificationPreferenceMongoRepository.class), mongo);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection(COLLECTION).deleteMany(new Document());
    }

    @Test
    void savesUnderAccountIdAndOverwritesInPlace() {
        adapter.save(NotificationPreferences.defaults("u1")
                .update(Map.of(NotificationCategory.WATCH, false, NotificationCategory.OFFER, false), NOW));
        adapter.save(
                adapter.find("u1").orElseThrow().update(Map.of(NotificationCategory.WATCH, true), NOW.plusSeconds(60)));

        assertThat(mongo.getDb().getCollection(COLLECTION).countDocuments()).isEqualTo(1);
        Document stored = mongo.getDb()
                .getCollection(COLLECTION)
                .find(new Document("_id", "u1"))
                .first();
        assertThat(stored).isNotNull();
        assertThat(stored.getList("disabledCategories", String.class)).containsExactly("OFFER");

        NotificationPreferences found = adapter.find("u1").orElseThrow();
        assertThat(found.getDisabled()).containsExactly(NotificationCategory.OFFER);
        assertThat(found.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(adapter.find("nobody")).isEmpty();
    }

    @Test
    void skipsUnknownStoredCategoryInsteadOfFailing() {
        mongo.getDb()
                .getCollection(COLLECTION)
                .insertOne(new Document("_id", "u1").append("disabledCategories", List.of("REMOVED_LATER", "WATCH")));

        assertThat(adapter.find("u1").orElseThrow().getDisabled()).containsExactly(NotificationCategory.WATCH);
    }

    @Test
    void findAccountsDisablingReturnsOnlyRequestedAccountsWithThatCategoryOff() {
        adapter.save(new NotificationPreferences("a", List.of(NotificationCategory.WATCH), NOW));
        adapter.save(new NotificationPreferences("b", List.of(NotificationCategory.OFFER), NOW));
        adapter.save(new NotificationPreferences(
                "c", List.of(NotificationCategory.WATCH, NotificationCategory.COMMUNITY), NOW));
        adapter.save(new NotificationPreferences("outside-page", List.of(NotificationCategory.WATCH), NOW));

        assertThat(adapter.findAccountsDisabling(NotificationCategory.WATCH, List.of("a", "b", "c", "d")))
                .containsExactlyInAnyOrder("a", "c");
        assertThat(adapter.findAccountsDisabling(NotificationCategory.WATCH, List.of()))
                .isEmpty();
    }

    @Test
    void update_mergesConcurrentChangesOnNewAndExistingDocuments() throws Exception {
        var service = new NotificationPreferenceService(adapter, Clock.fixed(NOW, ZoneOffset.UTC));
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 10; round++) {
                String accountId = "parallel-" + round;
                if (round % 2 == 0) {
                    adapter.save(new NotificationPreferences(accountId, List.of(NotificationCategory.OFFER), NOW));
                }
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var first = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 만료");
                    return service.update(accountId, Map.of(NotificationCategory.WATCH, false));
                });
                var second = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 만료");
                    return service.update(accountId, Map.of(NotificationCategory.COMMUNITY, false));
                });
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);

                var saved = service.get(accountId);
                assertThat(saved.getDisabled()).contains(NotificationCategory.WATCH, NotificationCategory.COMMUNITY);
                assertThat(saved.isEnabled(NotificationCategory.OFFER)).isEqualTo(round % 2 != 0);
                assertThat(saved.isEnabled(NotificationCategory.TRADE)).isTrue();
            }
        }
    }

    @Test
    void update_appliesEnablingAndDisablingTogetherWithoutResettingOtherCategories() {
        var service = new NotificationPreferenceService(adapter, Clock.fixed(NOW, ZoneOffset.UTC));
        service.update("mixed", Map.of(NotificationCategory.WATCH, false, NotificationCategory.OFFER, false));

        var saved = service.update(
                "mixed", Map.of(NotificationCategory.WATCH, true, NotificationCategory.COMMUNITY, false));

        assertThat(saved.getDisabled())
                .containsExactlyInAnyOrder(NotificationCategory.OFFER, NotificationCategory.COMMUNITY);
        assertThat(service.get("mixed").getDisabled()).isEqualTo(saved.getDisabled());
    }
}
