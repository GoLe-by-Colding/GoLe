package com.gole.api.chat.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 문의 Discord durable outbox 처리량·lease·백오프 설정. */
@Component
@ConfigurationProperties(prefix = "gole.support-notification-outbox")
public class SupportNotificationOutboxProperties {

    private boolean processingEnabled;
    private int batchSize = 20;
    private int maximumAttempts = 12;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration initialBackoff = Duration.ofSeconds(10);
    private Duration maximumBackoff = Duration.ofHours(1);
    private Duration terminalRetention = Duration.ofDays(30);

    public boolean isProcessingEnabled() {
        return processingEnabled;
    }

    public void setProcessingEnabled(boolean processingEnabled) {
        this.processingEnabled = processingEnabled;
    }

    public int getBatchSize() {
        return Math.clamp(batchSize, 1, 1_000);
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaximumAttempts() {
        return Math.max(1, maximumAttempts);
    }

    public void setMaximumAttempts(int maximumAttempts) {
        this.maximumAttempts = maximumAttempts;
    }

    public Duration getLeaseDuration() {
        return positive(leaseDuration, Duration.ofSeconds(30));
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getInitialBackoff() {
        return positive(initialBackoff, Duration.ofSeconds(10));
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaximumBackoff() {
        return positive(maximumBackoff, Duration.ofHours(1));
    }

    public void setMaximumBackoff(Duration maximumBackoff) {
        this.maximumBackoff = maximumBackoff;
    }

    public Duration getTerminalRetention() {
        return positive(terminalRetention, Duration.ofDays(30));
    }

    public void setTerminalRetention(Duration terminalRetention) {
        this.terminalRetention = terminalRetention;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
