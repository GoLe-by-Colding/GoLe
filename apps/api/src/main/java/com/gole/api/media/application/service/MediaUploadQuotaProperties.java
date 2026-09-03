package com.gole.api.media.application.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "gole.media.upload-rate-limit")
@Validated
public class MediaUploadQuotaProperties {

    @Min(1)
    private int maximumImages = 30;

    @NotNull
    private Duration window = Duration.ofMinutes(10);

    public int getMaximumImages() {
        return maximumImages;
    }

    public void setMaximumImages(int maximumImages) {
        this.maximumImages = maximumImages;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    @AssertTrue(message = "미디어 업로드 제한 시간창은 양수여야 합니다")
    public boolean isWindowPositive() {
        return window != null && !window.isZero() && !window.isNegative();
    }
}
