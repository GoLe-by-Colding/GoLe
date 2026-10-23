package com.gole.api.listing.application.port.in;

/** 입찰 체결과 같은 트랜잭션에서 현재 판매가를 확인하고 올린다. */
public interface RaiseListingPriceUseCase {

    void raisePriceTo(String listingId, String sellerId, long expectedPrice, long targetPrice);
}
