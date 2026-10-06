package com.gole.api.notification.domain.model;

/**
 * 알림 수신 설정의 단위. (notification-preferences)
 *
 * <p>사용자는 종류가 아니라 분류 단위로 끈다. 종류는 계속 늘어나지만 설정 화면의 토글은 늘리지 않기 위해서다.
 * {@link #TRADE}는 주문·결제·배송·분쟁처럼 돈과 물건이 걸린 진행 알림이라 끌 수 없다.
 */
public enum NotificationCategory {
    TRADE("거래 진행", true),
    OFFER("가격 제안·입찰", false),
    WATCH("찜·관심 세트", false),
    COMMUNITY("커뮤니티·부품 요청", false);

    private final String label;
    private final boolean mandatory;

    NotificationCategory(String label, boolean mandatory) {
        this.label = label;
        this.mandatory = mandatory;
    }

    public String label() {
        return label;
    }

    /** 끌 수 없는 분류인가. */
    public boolean mandatory() {
        return mandatory;
    }

    /** 외부 노출 키(소문자). */
    public String key() {
        return name().toLowerCase();
    }
}
