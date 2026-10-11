package com.gole.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.adapter.out.persistence.NotificationPreferenceMongoRepository;
import com.gole.api.notification.adapter.out.persistence.NotificationPreferencePersistenceAdapter;
import com.gole.api.notification.domain.model.NotificationCategory;
import com.gole.api.notification.domain.model.NotificationPreferences;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
}
