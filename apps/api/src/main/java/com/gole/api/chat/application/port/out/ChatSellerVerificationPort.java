package com.gole.api.chat.application.port.out;

/** Outbound port: 판매자 신원확인. 새 매물 방 개설과 직거래 확인 전에 판매자를 확인한다. */
public interface ChatSellerVerificationPort {

    void requireVerifiedSeller(String sellerId);
}
