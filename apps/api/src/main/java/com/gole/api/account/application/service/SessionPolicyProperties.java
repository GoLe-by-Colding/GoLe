package com.gole.api.account.application.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 불투명 세션의 수명 정책.
 *
 * <p>브라우저 쿠키의 수명만으로 세션 만료를 판단하지 않고 Redis 저장소에서 유휴 만료와 절대 만료를
 * 함께 강제한다. {@code rotationAge}가 지나면 명시적 갱신 요청에서 토큰을 교체하되 최초 발급 시각은
 * 보존하므로 반복 갱신으로 절대 수명을 우회할 수 없다.
 */
@Configuration
@ConfigurationProperties(prefix = "gole.session")
@Validated
public class SessionPolicyProperties {

    @NotNull
    private Duration absoluteTtl = Duration.ofDays(7);

    @NotNull
    private Duration idleTtl = Duration.ofDays(1);

    @NotNull
    private Duration rotationAge = Duration.ofHours(12);

    public Duration getAbsoluteTtl() {
        return absoluteTtl;
    }

    public void setAbsoluteTtl(Duration absoluteTtl) {
        this.absoluteTtl = absoluteTtl;
    }

    public Duration getIdleTtl() {
        return idleTtl;
    }

    public void setIdleTtl(Duration idleTtl) {
        this.idleTtl = idleTtl;
    }

    public Duration getRotationAge() {
        return rotationAge;
    }

    public void setRotationAge(Duration rotationAge) {
        this.rotationAge = rotationAge;
    }

    @AssertTrue(message = "세션 absolute-ttl, idle-ttl, rotation-age는 모두 양수여야 합니다")
    public boolean isPositive() {
        return isPositive(absoluteTtl) && isPositive(idleTtl) && isPositive(rotationAge);
    }

    @AssertTrue(message = "세션 idle-ttl과 rotation-age는 absolute-ttl보다 짧아야 합니다")
    public boolean isOrderingValid() {
        return absoluteTtl != null
                && idleTtl != null
                && rotationAge != null
                && idleTtl.compareTo(absoluteTtl) < 0
                && rotationAge.compareTo(absoluteTtl) < 0;
    }

    private static boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
