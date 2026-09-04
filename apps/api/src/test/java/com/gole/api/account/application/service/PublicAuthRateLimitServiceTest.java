package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.PublicAuthRateLimitPort;
import com.gole.api.account.application.port.out.PublicAuthRateLimitPort.Bucket;
import com.gole.api.account.application.port.out.PublicAuthRateLimitPort.Decision;
import com.gole.api.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;

class PublicAuthRateLimitServiceTest {

    private PublicAuthRateLimitPort rateLimit;
    private PublicAuthRateLimitService service;

    @BeforeEach
    void setUp() {
        rateLimit = mock(PublicAuthRateLimitPort.class);
        service = new PublicAuthRateLimitService(
                rateLimit,
                new PublicAuthRateLimitProperties(),
                Clock.fixed(Instant.parse("2026-09-04T00:00:30Z"), ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalizesAndHashesEmailAndClientInEveryRedisKey() {
        when(rateLimit.acquire(anyList())).thenReturn(Decision.allowedDecision());
        ArgumentCaptor<List<Bucket>> buckets = ArgumentCaptor.forClass(List.class);

        service.acquireRegistration(" Member@GoLe.Test ", "198.51.100.24");
        service.acquireVerificationResend("member@gole.test", "198.51.100.24");

        org.mockito.Mockito.verify(rateLimit, org.mockito.Mockito.times(2)).acquire(buckets.capture());
        List<Bucket> first = buckets.getAllValues().get(0);
        List<Bucket> second = buckets.getAllValues().get(1);
        assertThat(first)
                .extracting(Bucket::key)
                .containsExactlyElementsOf(second.stream().map(Bucket::key).toList());
        assertThat(first).extracting(Bucket::key).allSatisfy(key -> assertThat(key)
                .doesNotContainIgnoringCase("member", "gole.test")
                .doesNotContain("198.51.100.24"));
    }

    @Test
    void cooldownIsSilentOnlyForEnumerationSafeEmailRequests() {
        Duration retryAfter = Duration.ofSeconds(30);
        when(rateLimit.acquire(anyList())).thenReturn(Decision.rejectedDecision(5, retryAfter));

        assertThat(service.acquireVerificationResend("member@gole.test", "198.51.100.24"))
                .isFalse();
        assertThat(service.acquirePasswordReset("unknown@gole.test", "198.51.100.24"))
                .isFalse();
        assertThatThrownBy(() -> service.acquireRegistration("member@gole.test", "198.51.100.24"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasFieldOrPropertyWithValue("retryAfter", retryAfter);
    }

    @Test
    void hardLimitUsesSameGenericResponseForExistingAndUnknownEmailShapes() {
        Duration retryAfter = Duration.ofMinutes(5);
        when(rateLimit.acquire(anyList())).thenReturn(Decision.rejectedDecision(0, retryAfter));

        assertThatThrownBy(() -> service.acquireVerificationResend("member@gole.test", "198.51.100.24"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasFieldOrPropertyWithValue("code", "PUBLIC_AUTH_RATE_LIMITED")
                .hasMessage("요청이 잠시 많았습니다. 잠시 후 다시 시도해 주세요.");
        assertThatThrownBy(() -> service.acquirePasswordReset("unknown@gole.test", "198.51.100.24"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasFieldOrPropertyWithValue("code", "PUBLIC_AUTH_RATE_LIMITED")
                .hasMessage("요청이 잠시 많았습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void redisFailurePropagatesInsteadOfFailingOpen() {
        RedisConnectionFailureException unavailable = new RedisConnectionFailureException("connection refused");
        when(rateLimit.acquire(anyList())).thenThrow(unavailable);

        assertThatThrownBy(() -> service.acquirePasswordReset("member@gole.test", "198.51.100.24"))
                .isSameAs(unavailable);
        assertThatThrownBy(() -> service.acquireOAuthAuthorization("198.51.100.24"))
                .isSameAs(unavailable);
    }

    @Test
    void oauthUsesOnlyHashedClientBuckets() {
        when(rateLimit.acquire(anyList())).thenReturn(Decision.allowedDecision());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Bucket>> buckets = ArgumentCaptor.forClass(List.class);

        service.acquireOAuthAuthorization("2001:db8::5");

        org.mockito.Mockito.verify(rateLimit).acquire(buckets.capture());
        assertThat(buckets.getValue())
                .hasSize(4)
                .extracting(Bucket::key)
                .allSatisfy(key -> assertThat(key).startsWith("oauth:").doesNotContain("2001:db8::5"))
                .anySatisfy(key -> assertThat(key).startsWith("oauth:global:"));
    }
}
