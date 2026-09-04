package com.gole.api.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort.Challenge;
import com.gole.api.account.domain.model.Email;
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
class RedisPasswordResetChallengeStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisPasswordResetChallengeStoreAdapter store;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        store = new RedisPasswordResetChallengeStoreAdapter(redis);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void storesOnlyHashedEmailKeyAndConsumesChallengeExactlyOnce() {
        Email email = new Email("member@gole.test");
        Challenge challenge = new Challenge("account-1", "$2a$10$code-hash", Instant.parse("2026-09-03T00:00:00Z"), 0);

        store.store(email, challenge, Duration.ofMinutes(10));

        assertThat(store.find(email)).contains(challenge);
        Set<String> keys = redis.keys("gole:password-reset:*");
        assertThat(keys).hasSize(1).noneMatch(key -> key.contains(email.value()));
        String key = keys.iterator().next();
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS))
                .isBetween(1L, Duration.ofMinutes(10).toMillis());

        assertThatThrownBy(() -> store.store(email, challenge, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(redis.hasKey(key)).isFalse();

        store.store(email, challenge, Duration.ofMinutes(10));
        assertThat(store.incrementFailedAttempts(email)).isEqualTo(1);
        assertThat(store.find(email))
                .get()
                .extracting(Challenge::failedAttempts)
                .isEqualTo(1);
        assertThat(store.consume(email, "wrong-hash")).isFalse();
        assertThat(store.consume(email, challenge.codeHash())).isTrue();
        assertThat(store.consume(email, challenge.codeHash())).isFalse();
        assertThat(store.find(email)).isEmpty();
    }
}
