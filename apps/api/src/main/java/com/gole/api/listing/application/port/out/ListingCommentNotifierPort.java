package com.gole.api.listing.application.port.out;

/** Outbound port: 매물에 문의가 달렸음을 판매자에게 알린다. 구현은 실패를 흡수한다(best-effort). */
public interface ListingCommentNotifierPort {

    void notifySellerOfQuestion(String sellerId, String listingId, String listingTitle);
}
