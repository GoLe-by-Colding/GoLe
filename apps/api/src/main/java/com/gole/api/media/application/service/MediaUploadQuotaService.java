package com.gole.api.media.application.service;

import com.gole.api.common.exception.TooManyRequestsException;
import com.gole.api.media.application.port.in.AcquireMediaUploadQuotaUseCase;
import com.gole.api.media.application.port.out.MediaUploadQuotaPort;
import org.springframework.stereotype.Service;

/** 업로드 파일 수 기준의 사용자별 제한. 파일 크기·형식 검증은 기존 {@link MediaService}가 담당한다. */
@Service
public class MediaUploadQuotaService implements AcquireMediaUploadQuotaUseCase {

    private final MediaUploadQuotaPort quota;
    private final MediaUploadQuotaProperties properties;

    public MediaUploadQuotaService(MediaUploadQuotaPort quota, MediaUploadQuotaProperties properties) {
        this.quota = quota;
        this.properties = properties;
    }

    @Override
    public void acquire(String accountId, int imageCount) {
        if (imageCount < 1) {
            return;
        }
        MediaUploadQuotaPort.Decision decision =
                quota.acquire(accountId, imageCount, properties.getMaximumImages(), properties.getWindow());
        if (!decision.allowed()) {
            throw new TooManyRequestsException(
                    "MEDIA_UPLOAD_RATE_LIMITED", "이미지 업로드가 잠시 많았습니다. 잠시 후 다시 시도해 주세요.", decision.retryAfter());
        }
    }
}
