package com.gole.api.bid.application.port.out;

/** Outbound port: 체결한 입찰자에게 수락된 가격 제안을 만든다. (price-offer O21) */
public interface BidOfferPort {

    /** @return 만든 제안 id. 실패하면 예외를 그대로 올린다 — 서비스가 체결을 되돌린다 */
    String createAccepted(String listingId, String sellerId, String bidderId, long price);
}
