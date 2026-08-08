package com.gole.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.adapter.out.persistence.AccountMongoRepository;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.service.AccountAdminTransitionService;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

/** 다중 인스턴스에서 동시에 강등해도 최소 한 명의 ADMIN이 남는지 검증한다. */
@SpringBootTest
@Testcontainers
class AccountAdminConcurrencyIntegrationTest {

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
        registry.add("gole.media.seed-on-startup", () -> "false");
    }

    @Autowired
    AccountRepositoryPort accounts;

    @Autowired
    AccountAdminTransitionService transitions;

    @Autowired
    AccountMongoRepository mongoAccounts;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoAccounts.deleteAll();
        mongoTemplate.dropCollection("account_admin_fences");
        accounts.save(Account.provisioned(
                "admin-a", new Email("admin-a@gole.test"), new PasswordHash("plain:test"), Role.ADMIN));
        accounts.save(Account.provisioned(
                "admin-b", new Email("admin-b@gole.test"), new PasswordHash("plain:test"), Role.ADMIN));
    }

    @Test
    void concurrentDemotionsCannotRemoveEveryAdmin() throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        pool.submit(() -> demoteAfter(start, done, succeeded, "admin-a", "admin-b"));
        pool.submit(() -> demoteAfter(start, done, succeeded, "admin-b", "admin-a"));
        start.countDown();

        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(accounts.countByRole(Role.ADMIN)).isEqualTo(1);
    }

    private void demoteAfter(
            CountDownLatch start, CountDownLatch done, AtomicInteger succeeded, String target, String actor) {
        try {
            start.await();
            transitions.changeRole(target, actor, Role.USER);
            succeeded.incrementAndGet();
        } catch (RuntimeException | InterruptedException ignored) {
            // 한 요청은 fence write-conflict 또는 LAST_ADMIN 가드로 실패해야 한다.
        } finally {
            done.countDown();
        }
    }
}
