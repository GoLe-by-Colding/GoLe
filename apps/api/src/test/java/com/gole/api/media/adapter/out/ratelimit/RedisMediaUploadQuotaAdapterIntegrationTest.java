package com.gole.api.media.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisMediaUploadQuotaAdapterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisMediaUploadQuotaAdapter quota;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        quota = new RedisMediaUploadQuotaAdapter(
                redis, Clock.fixed(Instant.parse("2026-09-03T00:05:00Z"), ZoneOffset.UTC));
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void countsBatchImagesPerAccountInsideWindow() {
        assertThat(quota.acquire("account-a", 10, 30, Duration.ofMinutes(10)).allowed())
                .isTrue();
        assertThat(quota.acquire("account-a", 20, 30, Duration.ofMinutes(10)).allowed())
                .isTrue();
        var rejected = quota.acquire("account-a", 1, 30, Duration.ofMinutes(10));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMinutes(5));

        assertThat(quota.acquire("account-b", 1, 30, Duration.ofMinutes(10)).allowed())
                .isTrue();
    }
}
