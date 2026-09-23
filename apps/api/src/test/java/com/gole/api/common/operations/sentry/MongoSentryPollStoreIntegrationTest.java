package com.gole.api.common.operations.sentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClients;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MongoSentryPollStoreIntegrationTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Test
    @DisplayName("실제 Mongo는 lease 경쟁·만료복구·원장 멱등·전달 상태를 재시작 뒤 보존한다")
    void store_recoversLeaseAndPendingAfterRestart() {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var template = new MongoTemplate(client, "sentry_durable_test");
            var first = new MongoSentryPollStore(template);
            Instant now = Instant.parse("2026-09-12T00:00:00Z");
            var lease = first.acquire(now);
            assertThat(lease).isNotNull();
            assertThat(first.acquire(now)).isNull();
            var scan = new SentryPollStore.Scan(lease.scan().from(), now, "123:0:0", false);
            first.saveScan(lease, scan);
            first.enqueue("safe-hash", now);
            first.enqueue("safe-hash", now);
            assertThat(first.pending(now)).hasSize(1);
            first.deferRead(lease, now.plusSeconds(180));
            // 기존 owner를 해제하지 않아도 lease 만료 후 복구된다.
            var restarted = new MongoSentryPollStore(new MongoTemplate(client, "sentry_durable_test"));
            var recovered = restarted.acquire(now.plusSeconds(121));
            assertThat(recovered.scan()).isEqualTo(scan);
            assertThat(recovered.readNotBefore()).isEqualTo(now.plusSeconds(180));
            assertThatThrownBy(() -> first.saveScan(lease, scan)).hasMessage("SENTRY_LEASE_LOST");
            first.release(lease); // 이전 owner가 새 owner의 lease를 해제하지 못한다.
            assertThat(first.acquire(now.plusSeconds(121))).isNull();
            restarted.retry("safe-hash", now.plusSeconds(240));
            assertThat(restarted.pending(now.plusSeconds(121))).isEmpty();
            assertThat(restarted.hasPending()).isTrue();
            assertThat(restarted.pending(now.plusSeconds(241))).hasSize(1);
            restarted.delivered("safe-hash");
            restarted.enqueue("safe-hash", now);
            assertThat(restarted.hasPending()).isFalse();
            restarted.release(recovered);
            assertThat(first.acquire(now.plusSeconds(122))).isNotNull();
        }
    }
}
