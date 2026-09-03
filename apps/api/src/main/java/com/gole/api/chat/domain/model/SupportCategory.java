package com.gole.api.chat.domain.model;

/** 운영 문의 분류. 개인정보 권리 요청은 일반 문의와 분리해 운영 기한을 추적한다. */
public enum SupportCategory {
    GENERAL,
    TRADE,
    PAYMENT,
    PRODUCT_FEEDBACK,
    PRIVACY_ACCESS,
    PRIVACY_CORRECTION_DELETION,
    PRIVACY_PROCESSING_STOP;

    public boolean isPrivacyRightsRequest() {
        return this == PRIVACY_ACCESS || this == PRIVACY_CORRECTION_DELETION || this == PRIVACY_PROCESSING_STOP;
    }
}
