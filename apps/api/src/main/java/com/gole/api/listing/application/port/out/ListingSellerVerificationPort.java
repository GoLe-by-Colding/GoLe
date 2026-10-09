package com.gole.api.listing.application.port.out;

/**
 * Outbound port: 판매자 신원확인. 매물 문의는 주문·제안·채팅방 생성과 같은 "새 거래 연결"이라 같은 가드를 건다.
 *
 * <p>확인되지 않은 판매자면 계정 컨텍스트의 예외를 그대로 던진다.
 */
public interface ListingSellerVerificationPort {

    void requireVerifiedSeller(String sellerId);
}
