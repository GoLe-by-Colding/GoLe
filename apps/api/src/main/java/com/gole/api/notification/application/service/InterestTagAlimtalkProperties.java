package com.gole.api.notification.application.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 관심태그 알림톡의 템플릿·팬아웃·lease·백오프 설정. */
@ConfigurationProperties(prefix = "gole.interest-tag-alimtalk")
public class InterestTagAlimtalkProperties {

    private boolean enabled;
    private String templateId = "";
    private String themeVariable = "#{테마}";
    private String titleVariable = "#{매물명}";
    private String linkVariable = "#{링크}";
    private String listingUrlPrefix = "https://gole.co.kr/listings/";
    private int dailyLimitPerAccount = 3;
    private Duration quotaWindow = Duration.ofDays(1);
    private int recipientPageSize = 200;
    private int pagesPerLease = 5;
    private int maxRecipientsPerListing = 5_000;
    private Duration pollInterval = Duration.ofSeconds(10);
    private int batchSize = 20;
    private int maximumAttempts = 8;
    private Duration leaseDuration = Duration.ofSeconds(60);
    private Duration initialBackoff = Duration.ofSeconds(30);
    private Duration maximumBackoff = Duration.ofHours(1);
    private Duration terminalRetention = Duration.ofDays(30);

    public boolean enabled() {
        return enabled;
    }

    public String templateId() {
        return nullToEmpty(templateId);
    }

    public String themeVariable() {
        return textOr(themeVariable, "#{테마}");
    }

    public String titleVariable() {
        return textOr(titleVariable, "#{매물명}");
    }

    public String linkVariable() {
        return textOr(linkVariable, "#{링크}");
    }

    public String listingUrlPrefix() {
        return textOr(listingUrlPrefix, "https://gole.co.kr/listings/");
    }

    public int dailyLimitPerAccount() {
        return dailyLimitPerAccount;
    }

    public Duration quotaWindow() {
        return positive(quotaWindow, Duration.ofDays(1));
    }

    public int recipientPageSize() {
        return Math.clamp(recipientPageSize, 1, 1_000);
    }

    public int pagesPerLease() {
        return Math.clamp(pagesPerLease, 1, 100);
    }

    public int maxRecipientsPerListing() {
        return Math.max(1, maxRecipientsPerListing);
    }

    public Duration pollInterval() {
        return positive(pollInterval, Duration.ofSeconds(10));
    }

    public int batchSize() {
        return Math.clamp(batchSize, 1, 1_000);
    }

    public int maximumAttempts() {
        return Math.max(1, maximumAttempts);
    }

    public Duration leaseDuration() {
        return positive(leaseDuration, Duration.ofSeconds(60));
    }

    public Duration initialBackoff() {
        return positive(initialBackoff, Duration.ofSeconds(30));
    }

    public Duration maximumBackoff() {
        return positive(maximumBackoff, Duration.ofHours(1));
    }

    public Duration terminalRetention() {
        return positive(terminalRetention, Duration.ofDays(30));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public void setThemeVariable(String themeVariable) {
        this.themeVariable = themeVariable;
    }

    public void setTitleVariable(String titleVariable) {
        this.titleVariable = titleVariable;
    }

    public void setLinkVariable(String linkVariable) {
        this.linkVariable = linkVariable;
    }

    public void setListingUrlPrefix(String listingUrlPrefix) {
        this.listingUrlPrefix = listingUrlPrefix;
    }

    public void setDailyLimitPerAccount(int dailyLimitPerAccount) {
        this.dailyLimitPerAccount = dailyLimitPerAccount;
    }

    public void setQuotaWindow(Duration quotaWindow) {
        this.quotaWindow = quotaWindow;
    }

    public void setRecipientPageSize(int recipientPageSize) {
        this.recipientPageSize = recipientPageSize;
    }

    public void setPagesPerLease(int pagesPerLease) {
        this.pagesPerLease = pagesPerLease;
    }

    public void setMaxRecipientsPerListing(int maxRecipientsPerListing) {
        this.maxRecipientsPerListing = maxRecipientsPerListing;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setMaximumAttempts(int maximumAttempts) {
        this.maximumAttempts = maximumAttempts;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public void setMaximumBackoff(Duration maximumBackoff) {
        this.maximumBackoff = maximumBackoff;
    }

    public void setTerminalRetention(Duration terminalRetention) {
        this.terminalRetention = terminalRetention;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
