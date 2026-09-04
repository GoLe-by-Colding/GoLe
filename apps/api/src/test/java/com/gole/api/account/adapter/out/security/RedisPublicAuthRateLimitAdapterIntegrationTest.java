package com.gole.api.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.application.port.out.PublicAuthRateLimitPort.Bucket;
import com.gole.api.account.application.service.PublicAuthRateLimitProperties;
import com.gole.api.account.application.service.PublicAuthRateLimitService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class RedisPublicAuthRateLimitAdapterIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisPublicAuthRateLimitAdapter rateLimit;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        rateLimit = new RedisPublicAuthRateLimitAdapter(redis);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void parallelRequestsCannotExceedAtomicLimit() throws Exception {
        String key = "parallel:" + UUID.randomUUID();
        Bucket bucket = bucket(key, 5);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return rateLimit.acquire(List.of(bucket)).allowed();
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
        }
        assertThat(redis.opsForValue().get(RedisPublicAuthRateLimitAdapter.KEY_PREFIX + key))
                .isEqualTo("5");
    }

    @Test
    void rejectedMultiBucketRequestDoesNotPartiallyConsumeOtherBuckets() {
        String suffix = UUID.randomUUID().toString();
        Bucket roomy = bucket("roomy:" + suffix, 10);
        Bucket tight = bucket("tight:" + suffix, 1);

        assertThat(rateLimit.acquire(List.of(roomy, tight)).allowed()).isTrue();
        var rejected = rateLimit.acquire(List.of(roomy, tight));

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.rejectedBucket()).isEqualTo(1);
        assertThat(redis.opsForValue().get(RedisPublicAuthRateLimitAdapter.KEY_PREFIX + roomy.key()))
                .isEqualTo("1");
        assertThat(redis.opsForValue().get(RedisPublicAuthRateLimitAdapter.KEY_PREFIX + tight.key()))
                .isEqualTo("1");
    }

    @Test
    void repairsMissingExpiryBeforeRejectingAStaleCounter() {
        String key = "stale:" + UUID.randomUUID();
        redis.opsForValue().set(RedisPublicAuthRateLimitAdapter.KEY_PREFIX + key, "1");

        assertThat(rateLimit.acquire(List.of(bucket(key, 1))).allowed()).isFalse();

        assertThat(redis.getExpire(RedisPublicAuthRateLimitAdapter.KEY_PREFIX + key, TimeUnit.MILLISECONDS))
                .isPositive();
    }

    @Test
    void recipientCooldownIsAtomicUnderParallelRequests() throws Exception {
        PublicAuthRateLimitProperties properties = new PublicAuthRateLimitProperties();
        properties.setEmailRecipientCooldown(new PublicAuthRateLimitProperties.Limit(1, Duration.ofSeconds(5)));
        PublicAuthRateLimitService service = new PublicAuthRateLimitService(
                rateLimit, properties, Clock.fixed(Instant.parse("2026-09-04T00:00:30Z"), ZoneOffset.UTC));
        String email = "parallel-" + UUID.randomUUID() + "@gole.test";
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return service.acquireVerificationResend(email, "198.51.100.24");
                }));
            }
            start.countDown();

            long sends = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    sends++;
                }
            }
            assertThat(sends).isEqualTo(1);
        }
    }

    @Test
    void recipientCooldownAllowsOnlyAfterItsFullTtl() throws Exception {
        PublicAuthRateLimitProperties properties = new PublicAuthRateLimitProperties();
        properties.setEmailRecipientCooldown(new PublicAuthRateLimitProperties.Limit(1, Duration.ofMillis(100)));
        PublicAuthRateLimitService service = new PublicAuthRateLimitService(
                rateLimit, properties, Clock.fixed(Instant.parse("2026-09-04T00:02:30Z"), ZoneOffset.UTC));
        String email = "ttl-" + UUID.randomUUID() + "@gole.test";

        assertThat(service.acquireVerificationResend(email, "198.51.100.25")).isTrue();
        assertThat(service.acquireVerificationResend(email, "198.51.100.25")).isFalse();
        Thread.sleep(150);
        assertThat(service.acquireVerificationResend(email, "198.51.100.25")).isTrue();
    }

    @Test
    void oauthGlobalLimitCapsRequestsAcrossDifferentClientAddresses() {
        PublicAuthRateLimitProperties properties = new PublicAuthRateLimitProperties();
        properties.getOauthGlobalBurst().setMaximum(2);
        PublicAuthRateLimitService service = new PublicAuthRateLimitService(
                rateLimit, properties, Clock.fixed(Instant.parse("2026-09-04T01:05:00Z"), ZoneOffset.UTC));

        service.acquireOAuthAuthorization("198.51.100.1");
        service.acquireOAuthAuthorization("198.51.100.2");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.acquireOAuthAuthorization("198.51.100.3"))
                .isInstanceOf(com.gole.api.common.exception.TooManyRequestsException.class);
    }

    private static Bucket bucket(String key, int maximum) {
        return new Bucket(key, maximum, Duration.ofMinutes(2), Duration.ofMinutes(1));
    }
}
