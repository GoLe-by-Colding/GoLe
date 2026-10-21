package com.gole.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.catalog.adapter.out.persistence.LegoSetMongoRepository;
import com.gole.api.catalog.adapter.out.persistence.LegoSetPersistenceAdapter;
import com.gole.api.catalog.application.port.in.CreateLegoSetUseCase.CreateLegoSetCommand;
import com.gole.api.catalog.application.service.CatalogService;
import com.gole.api.catalog.domain.exception.LegoSetAlreadyExistsException;
import com.gole.api.catalog.domain.model.RetirementStatus;
import com.mongodb.client.MongoClients;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 관리자 신규 등록은 실제 저장소에서도 기존 기준 정보와 추천 상태를 보존해야 한다. */
@Testcontainers
class CatalogRegistrationIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Test
    @DisplayName("동일 번호의 신규 등록은 원본·추천·단종 상태를 덮어쓰지 않는다")
    void create_rejectsDuplicateWithoutChangingExistingSet() {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl())) {
            var mongo = new MongoTemplate(client, "catalog_duplicate_test");
            var adapter = adapter(mongo);
            var service = new CatalogService(adapter, adapter, (number, name, status) -> {});
            service.create(command("10307", "원본", RetirementStatus.ACTIVE, true));

            assertThatThrownBy(() -> service.create(command("10307", "덮어쓰기", RetirementStatus.RETIRED, false)))
                    .isInstanceOf(LegoSetAlreadyExistsException.class);

            assertThat(adapter.findAll(10)).singleElement().satisfies(stored -> {
                assertThat(stored.set().getName()).isEqualTo("원본");
                assertThat(stored.set().getRetirementStatus()).isEqualTo(RetirementStatus.ACTIVE);
                assertThat(stored.featured()).isTrue();
            });
        }
    }

    @Test
    @DisplayName("동시에 같은 번호를 등록하면 한 요청만 성공하고 승자의 값을 보존한다")
    void create_acceptsOnlyOneConcurrentRegistration() throws Exception {
        try (var client = MongoClients.create(MONGO.getReplicaSetUrl());
                var executor = Executors.newFixedThreadPool(2)) {
            var mongo = new MongoTemplate(client, "catalog_concurrent_test");
            var adapter = adapter(mongo);
            var service = new CatalogService(adapter, adapter, (number, name, status) -> {});
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> attempt(service, "첫 번째", ready, start));
            var second = executor.submit(() -> attempt(service, "두 번째", ready, start));
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            String a = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            String b = second.get(10, java.util.concurrent.TimeUnit.SECONDS);

            assertThat((a == null ? 0 : 1) + (b == null ? 0 : 1)).isEqualTo(1);
            assertThat(service.findBySetNumber("10308").getName()).isEqualTo(a == null ? b : a);
            assertThat(adapter.findAll(10)).hasSize(1);
        }
    }

    private static String attempt(CatalogService service, String name, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("경합 테스트 시작 대기 만료");
        }
        try {
            service.create(command("10308", name, RetirementStatus.ACTIVE, false));
            return name;
        } catch (LegoSetAlreadyExistsException conflict) {
            return null;
        }
    }

    private static LegoSetPersistenceAdapter adapter(MongoTemplate mongo) {
        return new LegoSetPersistenceAdapter(
                new MongoRepositoryFactory(mongo).getRepository(LegoSetMongoRepository.class));
    }

    private static CreateLegoSetCommand command(String number, String name, RetirementStatus status, boolean featured) {
        return new CreateLegoSetCommand(number, name, "Icons", 10, 2026, status, null, featured);
    }
}
