package com.gole.api.chat.application.port.out;

/** Outbound port: 직거래가 완료된 매물을 판매 완료로 바꾼다. */
public interface DirectTradeListingPort {

    /** 판매 중일 때만 바꾸고 바꿨으면 {@code true}. 이미 주문됐거나 팔렸으면 {@code false}. */
    boolean markSoldIfActive(String listingId);
}
