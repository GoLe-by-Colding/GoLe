package com.gole.api.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.out.AccountDeletionVerificationStorePort.Challenge;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisAccountDeletionVerificationStoreAdapterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisAccountDeletionVerificationStoreAdapter store;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        store = new RedisAccountDeletionVerificationStoreAdapter(redis);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void atomicallyStoresOnlyHashedAccountKeyWithTtlAndConsumesExactlyOnce() {
        String accountId = "account-sensitive-id";
        Challenge challenge = new Challenge("$2a$10$code-hash", Instant.parse("2026-09-04T00:00:00Z"), 0);

        store.store(accountId, challenge, Duration.ofMinutes(10));

        assertThat(store.find(accountId)).contains(challenge);
        Set<String> keys = redis.keys("gole:account-deletion-verification:*");
        assertThat(keys).hasSize(1).noneMatch(key -> key.contains(accountId));
        String key = keys.iterator().next();
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
                .isBetween(1L, Duration.ofMinutes(10).toMillis());

        assertThatThrownBy(() -> store.store(accountId, challenge, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.hasKey(key)).isFalse();

        store.store(accountId, challenge, Duration.ofMinutes(10));
        assertThat(store.incrementFailedAttempts(accountId)).isEqualTo(1);
        assertThat(store.consume(accountId, "wrong-hash")).isFalse();
        assertThat(store.consume(accountId, challenge.codeHash())).isTrue();
        assertThat(store.consume(accountId, challenge.codeHash())).isFalse();
        assertThat(store.find(accountId)).isEmpty();
    }
}
