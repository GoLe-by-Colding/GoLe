package com.gole.api.bid.application.port.out;

/** Outbound port: 입찰 알림. 구현은 장애를 흡수한다(best-effort). */
public interface BidNotifierPort {

    /** 판매자가 입찰가에 팔았다. (D7) */
    void bidFilled(String bidderId, String bidId, String listingId, long price, String setLabel);

    /** 입찰가 이하 매물이 올라왔다. (D8) */
    void listingMatched(String bidderId, String listingId, String title, long price);
}
