package com.gole.api.bid.application.port.out;

import java.time.Instant;

/** Outbound port: 체결한 입찰자에게 수락된 가격 제안을 만든다. (price-offer O21) */
public interface BidOfferPort {

    /** @return 만든 제안 id. 실패하면 예외를 그대로 올린다 — 서비스가 체결을 되돌린다 */
    String createAccepted(String listingId, String sellerId, String bidderId, long price);

    /** 체결로 만든 제안이 아직 그 입찰자가 이 매물에 쓸 수 있는 수락 상태인가(만료·철회·거절이면 false). */
    boolean isStillUsable(String offerId, String listingId, String bidderId, Instant now);
}
