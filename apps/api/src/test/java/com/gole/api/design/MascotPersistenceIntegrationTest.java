package com.gole.api.design;

import static org.assertj.core.api.Assertions.*;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.design.adapter.out.persistence.MongoMascotAdapter;
import com.gole.api.design.domain.model.MascotAsset;
import com.gole.api.design.domain.model.MascotSelection;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MascotPersistenceIntegrationTest {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Test
    @DisplayName("적용 리비전은 새 인스턴스에서도 읽히고, 같은 리비전 동시 적용은 한 건만 성공한다")
    void selectionsPersistAndFenceConcurrentPublication() throws Exception {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var first = new MongoMascotAdapter(new MongoTemplate(client, "mascot_test"));
            assertThat(first.currentSelection()).isEqualTo(MascotSelection.initial());

            var one = new MascotSelection(
                    1, "baby-round", "둥근 아기 고래", "admin-1", "first", "PUBLISH", Instant.parse("2026-10-10T00:00:00Z"));
            first.appendSelection(one);
            var second = new MongoMascotAdapter(new MongoTemplate(client, "mascot_test"));
            assertThat(second.currentSelection()).isEqualTo(one);

            var two = new MascotSelection(2, "tail-up", "꼬리 든 고래", "admin-2", "race", "PUBLISH", Instant.EPOCH);
            var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<Boolean> append = () -> {
                    start.await();
                    try {
                        second.appendSelection(two);
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                };
                var a = pool.submit(append);
                var b = pool.submit(append);
                start.countDown();
                assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(true, false);
            }
            assertThat(second.history(Long.MAX_VALUE))
                    .extracting(MascotSelection::revision)
                    .containsExactly(2L, 1L);
            assertThat(second.history(2)).containsExactly(one);
        }
    }

    @Test
    @DisplayName("업로드 에셋은 최근 것부터 나오고, 지우면 다시 찾을 수 없다")
    void assetsRoundTripAndDelete() {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var adapter = new MongoMascotAdapter(new MongoTemplate(client, "mascot_assets_test"));
            var older = new MascotAsset(
                    "a-1",
                    "옛 고래",
                    "",
                    "images/a.png",
                    "/api/v1/media/images/a.png",
                    null,
                    null,
                    64,
                    64,
                    "admin",
                    Instant.parse("2026-10-01T00:00:00Z"));
            var newer = new MascotAsset(
                    "a-2",
                    "새 고래",
                    "설명",
                    "images/b.png",
                    "/api/v1/media/images/b.png",
                    "images/c.png",
                    "/api/v1/media/images/c.png",
                    640,
                    360,
                    "admin",
                    Instant.parse("2026-10-10T00:00:00Z"));
            adapter.saveAsset(older);
            adapter.saveAsset(newer);

            assertThat(adapter.assets()).containsExactly(newer, older);
            assertThat(adapter.countAssets()).isEqualTo(2);
            assertThat(adapter.findAsset("a-2")).contains(newer);

            assertThat(adapter.deleteAsset("a-1")).isTrue();
            assertThat(adapter.deleteAsset("a-1")).isFalse();
            assertThat(adapter.findAsset("a-1")).isEmpty();
            assertThat(adapter.countAssets()).isEqualTo(1);
        }
    }
}
