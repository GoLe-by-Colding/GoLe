package com.gole.api.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InterestTagAlimtalkPropertiesTest {

    @Test
    void exposesSafeDefaults() {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.templateId()).isEmpty();
        assertThat(properties.themeVariable()).isEqualTo("#{테마}");
        assertThat(properties.titleVariable()).isEqualTo("#{매물명}");
        assertThat(properties.linkVariable()).isEqualTo("#{링크}");
        assertThat(properties.listingUrlPrefix()).isEqualTo("https://gole.co.kr/listings/");
        assertThat(properties.dailyLimitPerAccount()).isEqualTo(3);
        assertThat(properties.quotaWindow()).isEqualTo(Duration.ofDays(1));
        assertThat(properties.recipientPageSize()).isEqualTo(200);
        assertThat(properties.pagesPerLease()).isEqualTo(5);
        assertThat(properties.maxRecipientsPerListing()).isEqualTo(5_000);
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.batchSize()).isEqualTo(20);
        assertThat(properties.maximumAttempts()).isEqualTo(8);
        assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maximumBackoff()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.terminalRetention()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void fallsBackFromBlankTemplateVariablesAndListingUrl() {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setTemplateId(null);
        properties.setThemeVariable(" ");
        properties.setTitleVariable(null);
        properties.setLinkVariable("\t");
        properties.setListingUrlPrefix("");

        assertThat(properties.templateId()).isEmpty();
        assertThat(properties.themeVariable()).isEqualTo("#{테마}");
        assertThat(properties.titleVariable()).isEqualTo("#{매물명}");
        assertThat(properties.linkVariable()).isEqualTo("#{링크}");
        assertThat(properties.listingUrlPrefix()).isEqualTo("https://gole.co.kr/listings/");
    }

    @Test
    void fallsBackFromNullZeroAndNegativeDurations() {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setQuotaWindow(Duration.ZERO);
        properties.setPollInterval(Duration.ofSeconds(-1));
        properties.setLeaseDuration(null);
        properties.setInitialBackoff(Duration.ZERO);
        properties.setMaximumBackoff(Duration.ofSeconds(-1));
        properties.setTerminalRetention(null);

        assertThat(properties.quotaWindow()).isEqualTo(Duration.ofDays(1));
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.leaseDuration()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.initialBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maximumBackoff()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.terminalRetention()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void clampsFanoutAndWorkerSizesToDefensiveBounds() {
        InterestTagAlimtalkProperties properties = new InterestTagAlimtalkProperties();
        properties.setRecipientPageSize(0);
        properties.setPagesPerLease(101);
        properties.setMaxRecipientsPerListing(0);
        properties.setBatchSize(10_001);
        properties.setMaximumAttempts(0);

        assertThat(properties.recipientPageSize()).isEqualTo(1);
        assertThat(properties.pagesPerLease()).isEqualTo(100);
        assertThat(properties.maxRecipientsPerListing()).isEqualTo(1);
        assertThat(properties.batchSize()).isEqualTo(1_000);
        assertThat(properties.maximumAttempts()).isEqualTo(1);
    }
}
