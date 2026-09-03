package com.gole.api.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.application.port.out.SessionStorePort.SessionPrincipal;
import com.gole.api.account.domain.model.Role;
import java.time.Duration;
import java.time.Instant;
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
class RedisSessionStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisSessionStoreAdapter sessions;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        sessions = new RedisSessionStoreAdapter(redis);
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void storesVersionedMetadataAndStillReadsLegacySessions() {
        Instant issuedAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant rotatedAt = Instant.parse("2026-09-01T12:00:00Z");
        sessions.store("v2-token", "account-1", Role.ADMIN, issuedAt, rotatedAt, Duration.ofHours(4));

        assertThat(sessions.resolve("v2-token"))
                .contains(new SessionPrincipal("account-1", Role.ADMIN, issuedAt, rotatedAt));

        redis.opsForValue().set("gole:session:legacy-token", "account-2|USER", Duration.ofHours(1));
        assertThat(sessions.resolve("legacy-token")).contains(new SessionPrincipal("account-2", Role.USER));
    }

    @Test
    void malformedVersionedSessionFailsClosedAndAccountRevokeRemovesEveryToken() {
        redis.opsForValue().set("gole:session:malformed", "v2|account-1|SUPERUSER|0|0", Duration.ofHours(1));
        assertThat(sessions.resolve("malformed")).isEmpty();

        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        sessions.store("one", "account-revoke", Role.USER, now, now, Duration.ofHours(1));
        sessions.store("two", "account-revoke", Role.USER, now, now, Duration.ofHours(1));
        sessions.revokeAllForAccount("account-revoke");

        assertThat(sessions.resolve("one")).isEmpty();
        assertThat(sessions.resolve("two")).isEmpty();
    }

    @Test
    void shorterSessionNeverShrinksAccountRevocationIndex() {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        sessions.store("long", "account-index", Role.USER, now, now, Duration.ofHours(4));
        sessions.store("short", "account-index", Role.USER, now, now, Duration.ofHours(1));
        sessions.touch("short", "account-index", Duration.ofMinutes(30));

        Long remainingMillis = redis.getExpire("gole:session:acct:account-index", TimeUnit.MILLISECONDS);
        assertThat(remainingMillis)
                .isNotNull()
                .isGreaterThan(Duration.ofHours(3).toMillis());
    }
}
