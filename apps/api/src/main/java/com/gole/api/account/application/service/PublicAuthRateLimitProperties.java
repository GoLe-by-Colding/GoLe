package com.gole.api.account.application.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** 공개 인증 요청의 기본 한도. 모든 제한은 항상 활성화되며 Redis 장애 시 요청을 허용하지 않는다. */
@Configuration
@ConfigurationProperties(prefix = "gole.auth.public-rate-limit")
@Validated
public class PublicAuthRateLimitProperties {

    @Valid
    private Limit emailRecipientCooldown = new Limit(1, Duration.ofMinutes(1));

    @Valid
    private Limit emailRecipientDaily = new Limit(8, Duration.ofDays(1));

    @Valid
    private Limit emailClientBurst = new Limit(5, Duration.ofMinutes(1));

    @Valid
    private Limit emailClientHourly = new Limit(30, Duration.ofHours(1));

    @Valid
    private Limit emailGlobalBurst = new Limit(60, Duration.ofMinutes(1));

    @Valid
    private Limit emailGlobalDaily = new Limit(300, Duration.ofDays(1));

    @Valid
    private Limit oauthClientBurst = new Limit(20, Duration.ofMinutes(1));

    @Valid
    private Limit oauthClientHourly = new Limit(120, Duration.ofHours(1));

    @Valid
    private Limit oauthGlobalBurst = new Limit(120, Duration.ofMinutes(1));

    @Valid
    private Limit oauthGlobalDaily = new Limit(2_000, Duration.ofDays(1));

    public Limit getEmailRecipientCooldown() {
        return emailRecipientCooldown;
    }

    public void setEmailRecipientCooldown(Limit emailRecipientCooldown) {
        this.emailRecipientCooldown = emailRecipientCooldown;
    }

    public Limit getEmailRecipientDaily() {
        return emailRecipientDaily;
    }

    public void setEmailRecipientDaily(Limit emailRecipientDaily) {
        this.emailRecipientDaily = emailRecipientDaily;
    }

    public Limit getEmailClientBurst() {
        return emailClientBurst;
    }

    public void setEmailClientBurst(Limit emailClientBurst) {
        this.emailClientBurst = emailClientBurst;
    }

    public Limit getEmailClientHourly() {
        return emailClientHourly;
    }

    public void setEmailClientHourly(Limit emailClientHourly) {
        this.emailClientHourly = emailClientHourly;
    }

    public Limit getEmailGlobalBurst() {
        return emailGlobalBurst;
    }

    public void setEmailGlobalBurst(Limit emailGlobalBurst) {
        this.emailGlobalBurst = emailGlobalBurst;
    }

    public Limit getEmailGlobalDaily() {
        return emailGlobalDaily;
    }

    public void setEmailGlobalDaily(Limit emailGlobalDaily) {
        this.emailGlobalDaily = emailGlobalDaily;
    }

    public Limit getOauthClientBurst() {
        return oauthClientBurst;
    }

    public void setOauthClientBurst(Limit oauthClientBurst) {
        this.oauthClientBurst = oauthClientBurst;
    }

    public Limit getOauthClientHourly() {
        return oauthClientHourly;
    }

    public void setOauthClientHourly(Limit oauthClientHourly) {
        this.oauthClientHourly = oauthClientHourly;
    }

    public Limit getOauthGlobalBurst() {
        return oauthGlobalBurst;
    }

    public void setOauthGlobalBurst(Limit oauthGlobalBurst) {
        this.oauthGlobalBurst = oauthGlobalBurst;
    }

    public Limit getOauthGlobalDaily() {
        return oauthGlobalDaily;
    }

    public void setOauthGlobalDaily(Limit oauthGlobalDaily) {
        this.oauthGlobalDaily = oauthGlobalDaily;
    }

    public static class Limit {

        @Min(1)
        private int maximum = 1;

        @NotNull
        private Duration window = Duration.ofMinutes(1);

        public Limit() {}

        public Limit(int maximum, Duration window) {
            this.maximum = maximum;
            this.window = window;
        }

        public int getMaximum() {
            return maximum;
        }

        public void setMaximum(int maximum) {
            this.maximum = maximum;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        @AssertTrue(message = "공개 인증 요청 제한 시간창은 양수여야 합니다")
        public boolean isWindowPositive() {
            return window != null && window.toMillis() >= 1;
        }
    }
}
