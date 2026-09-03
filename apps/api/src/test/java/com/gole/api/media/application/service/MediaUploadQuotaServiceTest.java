package com.gole.api.media.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.TooManyRequestsException;
import com.gole.api.media.application.port.out.MediaUploadQuotaPort;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaUploadQuotaServiceTest {

    @Test
    void rejectsWhenAccountWindowIsExhausted() {
        MediaUploadQuotaProperties properties = new MediaUploadQuotaProperties();
        properties.setMaximumImages(30);
        properties.setWindow(Duration.ofMinutes(10));
        MediaUploadQuotaService service = new MediaUploadQuotaService(
                (accountId, imageCount, maximumImages, window) ->
                        new MediaUploadQuotaPort.Decision(false, Duration.ofSeconds(42)),
                properties);

        assertThatThrownBy(() -> service.acquire("account-1", 5))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting("code", "retryAfter")
                .containsExactly("MEDIA_UPLOAD_RATE_LIMITED", Duration.ofSeconds(42));
    }
}
