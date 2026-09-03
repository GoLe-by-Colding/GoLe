package com.gole.api.media.application.port.in;

/** 인증된 사용자의 이미지 업로드 사용량을 원자적으로 차감한다. */
public interface AcquireMediaUploadQuotaUseCase {

    void acquire(String accountId, int imageCount);
}
