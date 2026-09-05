package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.PublicAuthRequestLimitUseCase;
import com.gole.api.account.application.port.out.PublicAuthRateLimitPort;
import com.gole.api.account.application.port.out.PublicAuthRateLimitPort.Bucket;
import com.gole.api.account.application.port.out.PublicAuthRateLimitPort.Decision;
import com.gole.api.account.application.service.PublicAuthRateLimitProperties.Limit;
import com.gole.api.account.domain.model.Email;
import com.gole.api.common.exception.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 공개 인증 요청을 IP·수신자·전체 시간창으로 제한한다.
 *
 * <p>키에는 이메일과 IP 원문을 넣지 않는다. 존재하지 않는 이메일도 조회 전에 똑같이 한도를 획득하므로
 * Redis 키와 429 응답을 계정 존재 여부 오라클로 사용할 수 없다. 저장소 예외는 일부러 잡지 않아 Redis가
 * 고장 나도 인증 메일·OAuth state를 무제한 발급하는 fail-open 상태가 되지 않는다.
 */
@Service
public class PublicAuthRateLimitService implements PublicAuthRequestLimitUseCase {

    private static final int RECIPIENT_COOLDOWN_BUCKET = 5;
    private static final String ERROR_CODE = "PUBLIC_AUTH_RATE_LIMITED";
    private static final String ERROR_MESSAGE = "요청이 잠시 많았습니다. 잠시 후 다시 시도해 주세요.";

    private final PublicAuthRateLimitPort rateLimit;
    private final PublicAuthRateLimitProperties properties;
    private final Clock clock;

    public PublicAuthRateLimitService(
            PublicAuthRateLimitPort rateLimit, PublicAuthRateLimitProperties properties, Clock clock) {
        this.rateLimit = rateLimit;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void acquireRegistration(String email, String clientAddress) {
        acquireEmail(email, clientAddress, false);
    }

    @Override
    public boolean acquireVerificationResend(String email, String clientAddress) {
        return acquireEmail(email, clientAddress, true);
    }

    @Override
    public boolean acquirePasswordReset(String email, String clientAddress) {
        return acquireEmail(email, clientAddress, true);
    }

    @Override
    public void acquireOAuthAuthorization(String clientAddress) {
        String client = digest(normalizeClient(clientAddress));
        long nowMillis = clock.millis();
        Decision decision = rateLimit.acquire(List.of(
                bucket("oauth:client:burst:" + client, properties.getOauthClientBurst(), nowMillis),
                bucket("oauth:client:hourly:" + client, properties.getOauthClientHourly(), nowMillis),
                bucket("oauth:global:burst", properties.getOauthGlobalBurst(), nowMillis),
                bucket("oauth:global:daily", properties.getOauthGlobalDaily(), nowMillis)));
        rejectIfNeeded(decision);
    }

    private boolean acquireEmail(String rawEmail, String clientAddress, boolean silentlyApplyCooldown) {
        String recipient = digest(new Email(rawEmail).value());
        String client = digest(normalizeClient(clientAddress));
        long nowMillis = clock.millis();
        Decision decision = rateLimit.acquire(List.of(
                bucket("email:client:burst:" + client, properties.getEmailClientBurst(), nowMillis),
                bucket("email:client:hourly:" + client, properties.getEmailClientHourly(), nowMillis),
                bucket("email:global:burst", properties.getEmailGlobalBurst(), nowMillis),
                bucket("email:global:daily", properties.getEmailGlobalDaily(), nowMillis),
                bucket("email:recipient:daily:" + recipient, properties.getEmailRecipientDaily(), nowMillis),
                cooldownBucket("email:recipient:cooldown:" + recipient, properties.getEmailRecipientCooldown())));
        if (decision.allowed()) {
            return true;
        }
        if (silentlyApplyCooldown && decision.rejectedBucket() == RECIPIENT_COOLDOWN_BUCKET) {
            return false;
        }
        throw limited(decision.retryAfter());
    }

    private void rejectIfNeeded(Decision decision) {
        if (!decision.allowed()) {
            throw limited(decision.retryAfter());
        }
    }

    private static Bucket bucket(String key, Limit limit, long nowMillis) {
        long windowMillis = limit.getWindow().toMillis();
        long windowNumber = Math.floorDiv(nowMillis, windowMillis);
        long retryMillis = windowMillis - Math.floorMod(nowMillis, windowMillis);
        return new Bucket(
                key + ":" + windowNumber,
                limit.getMaximum(),
                limit.getWindow().multipliedBy(2),
                Duration.ofMillis(Math.max(1, retryMillis)));
    }

    /** 정렬된 고정 창과 달리 첫 발급 시점부터 정확히 60초를 보장하는 TTL 잠금이다. */
    private static Bucket cooldownBucket(String key, Limit limit) {
        return new Bucket(key, limit.getMaximum(), limit.getWindow(), limit.getWindow());
    }

    private static TooManyRequestsException limited(Duration retryAfter) {
        return new TooManyRequestsException(ERROR_CODE, ERROR_MESSAGE, retryAfter);
    }

    private static String normalizeClient(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank()
                ? "unknown"
                : clientAddress.trim().toLowerCase();
    }

    private static String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
