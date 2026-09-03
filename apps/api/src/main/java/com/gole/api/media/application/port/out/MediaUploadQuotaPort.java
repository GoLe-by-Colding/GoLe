package com.gole.api.media.application.port.out;

import java.time.Duration;

/** 사용자별 고정 시간창 업로드 사용량 저장소. */
public interface MediaUploadQuotaPort {

    Decision acquire(String accountId, int imageCount, int maximumImages, Duration window);

    record Decision(boolean allowed, Duration retryAfter) {}
}
