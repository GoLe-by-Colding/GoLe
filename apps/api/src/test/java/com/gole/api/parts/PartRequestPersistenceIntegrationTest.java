package com.gole.api.parts;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.adapter.out.persistence.AccountDeletionRequestMongoRepository;
import com.gole.api.account.adapter.out.persistence.MongoAccountDeletionAdapter;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountDeletionStatus;
import com.gole.api.account.support.AccountLinkedRecordsWiring;
import com.gole.api.parts.adapter.out.persistence.MongoPartRequestAdapter;
import com.gole.api.parts.adapter.out.persistence.PartRequestDocument;
import com.gole.api.parts.domain.model.PartRequest;
import com.gole.api.parts.domain.model.PartRequestStatus;
import com.gole.api.parts.domain.model.WantedPart;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 실제 Mongo에서 부품 요청 저장·조회 순서·필터, 조건부 마감, 계정 삭제 파기(W11)를 검증한다. */
@Testcontainers
class PartRequestPersistenceIntegrationTest {

    // Mongo Date는 밀리초까지라 왕복 비교가 깨지지 않도록 초 단위 시각만 쓴다.
    private static final Instant T0 = Instant.parse("2026-10-06T00:00:00Z");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static MongoClient client;
    static MongoTemplate mongo;
    static MongoPartRequestAdapter adapter;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getReplicaSetUrl());
        mongo = new MongoTemplate(client, "gole_part_request_test");
        adapter = new MongoPartRequestAdapter(mongo);
        // 운영은 auto-index-creation 이 만든다. 여기서는 같은 애노테이션 정의를 해석해 실제로 만들어 본다.
        IndexOperations indexes = mongo.indexOps(PartRequestDocument.class);
        new MongoPersistentEntityIndexResolver(mongo.getConverter().getMappingContext())
                .resolveIndexFor(PartRequestDocument.class)
                .forEach(indexes::createIndex);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void clean() {
        mongo.getDb().getCollection("part_requests").deleteMany(new Document());
    }

    private static PartRequest request(String id, String requesterId, String setNumber, int minutesAfterT0) {
        return PartRequest.create(
                id,
                requesterId,
                setNumber,
                List.of(new WantedPart("3062b", "Black", 4), new WantedPart("3001", "Red", 2)),
                "메모 " + id,
                T0.plusSeconds(60L * minutesAfterT0));
    }

    @Test
    @DisplayName("모든 필드가 왕복하고, 세트 없는 요청은 setNumber가 null로 돌아온다")
    void roundTripsAllFields() {
        adapter.save(request("pr-set", "user-1", "10305", 0));
        adapter.save(request("pr-noset", "user-1", null, 1));

        PartRequest loaded = adapter.findById("pr-set").orElseThrow();
        assertThat(loaded.getRequesterId()).isEqualTo("user-1");
        assertThat(loaded.getSetNumber()).isEqualTo("10305");
        assertThat(loaded.getItems())
                .containsExactly(new WantedPart("3062b", "Black", 4), new WantedPart("3001", "Red", 2));
        assertThat(loaded.getNote()).isEqualTo("메모 pr-set");
        assertThat(loaded.getStatus()).isEqualTo(PartRequestStatus.OPEN);
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getClosedAt()).isNull();

        assertThat(adapter.findById("pr-noset").orElseThrow().getSetNumber()).isNull();
        assertThat(adapter.findById("missing")).isEmpty();

        Document raw = mongo.getDb()
                .getCollection("part_requests")
                .find(new Document("_id", "pr-set"))
                .first();
        assertThat(raw).isNotNull();
        assertThat(raw.getString("status")).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("게시판은 세트·상태로 거르고 최신순, limit을 지킨다")
    void searchFiltersByStatusAndSetNewestFirst() {
        adapter.save(request("a", "user-1", "10305", 0));
        adapter.save(request("b", "user-2", "75192", 1));
        adapter.save(request("c", "user-1", "10305", 2));
        adapter.save(request("d", "user-3", null, 3));
        PartRequest closed = request("e", "user-2", "10305", 4).close(T0.plusSeconds(600));
        adapter.save(closed);

        assertThat(adapter.search(null, PartRequestStatus.OPEN, 20))
                .extracting(PartRequest::getId)
                .containsExactly("d", "c", "b", "a");
        assertThat(adapter.search("10305", PartRequestStatus.OPEN, 20))
                .extracting(PartRequest::getId)
                .containsExactly("c", "a");
        assertThat(adapter.search("10305", PartRequestStatus.CLOSED, 20))
                .extracting(PartRequest::getId)
                .containsExactly("e");
        assertThat(adapter.search("10305", null, 20))
                .extracting(PartRequest::getId)
                .containsExactly("e", "c", "a");
        assertThat(adapter.search(null, null, 2)).extracting(PartRequest::getId).containsExactly("e", "d");

        PartRequest loadedClosed = adapter.findById("e").orElseThrow();
        assertThat(loadedClosed.getStatus()).isEqualTo(PartRequestStatus.CLOSED);
        assertThat(loadedClosed.getClosedAt()).isEqualTo(T0.plusSeconds(600));
    }

    @Test
    @DisplayName("내 요청은 상태 무관 최신순, 열린 요청 수는 OPEN만 센다")
    void findByRequesterAndCountOpen() {
        adapter.save(request("a", "user-1", "10305", 0));
        adapter.save(request("b", "user-1", null, 1));
        adapter.save(request("c", "user-2", null, 2));
        adapter.save(request("d", "user-1", null, 3).close(T0.plusSeconds(600)));

        assertThat(adapter.findByRequester("user-1", 50))
                .extracting(PartRequest::getId)
                .containsExactly("d", "b", "a");
        assertThat(adapter.findByRequester("user-1", 2))
                .extracting(PartRequest::getId)
                .containsExactly("d", "b");
        assertThat(adapter.countOpenByRequester("user-1")).isEqualTo(2);
        assertThat(adapter.countOpenByRequester("nobody")).isZero();
    }

    @Test
    @DisplayName("조건부 마감은 열린 요청에만 반영되고, 지운 요청을 되살리지 않는다")
    void saveClosureIfOpenNeverResurrectsOrReclosed() {
        PartRequest open = adapter.save(request("a", "user-1", null, 0));
        PartRequest closed = open.close(T0.plusSeconds(60));

        assertThat(adapter.saveClosureIfOpen(closed)).isTrue();
        assertThat(adapter.findById("a").orElseThrow().getClosedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(adapter.saveClosureIfOpen(open.close(T0.plusSeconds(120)))).isFalse();
        assertThat(adapter.findById("a").orElseThrow().getClosedAt()).isEqualTo(T0.plusSeconds(60));

        PartRequest other = adapter.save(request("b", "user-1", null, 1));
        adapter.deleteById("b");
        assertThat(adapter.saveClosureIfOpen(other.close(T0.plusSeconds(60)))).isFalse();
        assertThat(adapter.findById("b")).isEmpty();
    }

    @Test
    @DisplayName("스펙의 세 인덱스가 실제로 만들어진다")
    void declaresSpecifiedIndexes() {
        Set<String> names = mongo.indexOps(PartRequestDocument.class).getIndexInfo().stream()
                .map(info -> info.getName())
                .collect(Collectors.toSet());

        assertThat(names).contains("ix_status_createdAt", "ix_setNumber_status_createdAt", "ix_requesterId_createdAt");
    }

    @Test
    @DisplayName("계정 삭제가 그 계정의 부품 요청만 지운다 (W11)")
    void accountDeletionPurgesRequesterPartRequests() {
        String accountId = "account-parts-delete";
        String requestId = "deletion-parts-1";
        mongo.getDb().getCollection("accounts").deleteMany(new Document());
        mongo.getDb().getCollection("account_deletion_requests").deleteMany(new Document());
        mongo.getDb()
                .getCollection("accounts")
                .insertOne(new Document("_id", accountId)
                        .append("email", "parts@gole.test")
                        .append("status", "SUSPENDED")
                        .append("role", "USER")
                        .append("suspendedReason", AccountDeletionRequest.suspensionReason(requestId)));
        adapter.save(request("mine-open", accountId, "10305", 0));
        adapter.save(request("mine-closed", accountId, null, 1).close(T0.plusSeconds(600)));
        adapter.save(request("theirs", "someone-else", "10305", 2));

        var deletions = new MongoAccountDeletionAdapter(
                new MongoRepositoryFactory(mongo).getRepository(AccountDeletionRequestMongoRepository.class),
                mongo,
                AccountLinkedRecordsWiring.realParticipants(mongo));
        deletions.save(AccountDeletionRequest.requested(
                requestId, accountId, "key-hash", "fingerprint", List.of(), T0.minusSeconds(60)));

        AccountDeletionRequest completed =
                deletions.complete(requestId, accountId, "admin-1", "completion-hash", "completion-fp", T0);

        assertThat(completed.getStatus()).isEqualTo(AccountDeletionStatus.COMPLETED);
        assertThat(completed.getDeletionCounts()).containsEntry("partRequests", 2L);
        assertThat(adapter.findById("mine-open")).isEmpty();
        assertThat(adapter.findById("mine-closed")).isEmpty();
        assertThat(adapter.findById("theirs")).isPresent();
    }
}
