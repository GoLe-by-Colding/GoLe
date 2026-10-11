package com.gole.api.offer.application.port.out;

/**
 * Outbound port: 판매자 신원확인. 제안은 주문·채팅방 생성과 같은 "새 판매 행동"이라 같은 가드를 건다.
 * (price-offer O2)
 */
public interface SellerVerificationPort {

    /** 확인되지 않은 판매자면 계정 컨텍스트의 예외를 그대로 던진다. */
    void requireVerifiedSeller(String sellerId);
}
