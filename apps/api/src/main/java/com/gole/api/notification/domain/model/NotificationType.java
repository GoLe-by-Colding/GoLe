package com.gole.api.notification.domain.model;

/**
 * 알림 종류. (알림 스펙 N1)
 *
 * <p>종류마다 수신 설정 분류({@link NotificationCategory})가 하나씩 붙는다. 새 종류를 더하면 분류도 같이
 * 정해야 한다 — 빠뜨리면 컴파일이 실패하도록 생성자 인자로 받는다.
 */
public enum NotificationType {
    ORDER_PLACED(NotificationCategory.TRADE),
    ORDER_PAID(NotificationCategory.TRADE),
    COMMENT(NotificationCategory.COMMUNITY),
    POST_LIKED(NotificationCategory.COMMUNITY),
    FOLLOW(NotificationCategory.COMMUNITY),
    /** 팔로우한 셀러의 새 매물. */
    NEW_LISTING(NotificationCategory.WATCH),
    /** 관심·보유 세트의 단종 상태 변경(단종 임박·단종). */
    SET_RETIREMENT(NotificationCategory.WATCH),
    /** 관심 세트에 새 매물이 등록됨. */
    WATCHED_SET_LISTING(NotificationCategory.WATCH),
    /** 찜한 매물의 가격이 내려감. (listing-edit-and-bump) */
    LISTING_PRICE_DROPPED(NotificationCategory.WATCH),
    /** 판매자에게: 내 매물에 가격 제안이 들어옴. (price-offer) */
    OFFER_RECEIVED(NotificationCategory.OFFER),
    /** 구매자에게: 내 가격 제안이 수락됨. (price-offer) */
    OFFER_ACCEPTED(NotificationCategory.OFFER),
    /** 구매자에게: 내 가격 제안이 거절·취소됨. (price-offer) */
    OFFER_DECLINED(NotificationCategory.OFFER),
    /** 입찰자에게: 내 입찰가 이하로 매물이 올라옴. (buy-bids) */
    BID_LISTING_MATCHED(NotificationCategory.OFFER),
    /** 입찰자에게: 판매자가 내 입찰가에 판매를 수락함. (buy-bids) */
    BID_FILLED(NotificationCategory.OFFER),
    /** 보유자에게: 내가 가진 세트의 부족 부품을 찾는 요청이 올라옴. (wanted-parts) */
    PART_REQUEST_FOR_OWNED_SET(NotificationCategory.COMMUNITY),
    /** 직거래 확인·분쟁·배송 등 거래 진행 알림. */
    GENERAL(NotificationCategory.TRADE);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }
}
