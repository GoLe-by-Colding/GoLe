package com.gole.api.listing.application.port.out;

/**
 * 수정으로 매물 가격이 내려갔을 때 그 매물을 찜한 사용자에게 알리는 출력 포트. (E8, E9)
 *
 * <p>best-effort다. 구현은 수신자 조회·발송 장애를 흡수해야 하며, 수정은 이 포트 때문에 실패하지 않는다.
 */
public interface ListingPriceDropNotifierPort {

    void priceDropped(String listingId, String sellerId, String title, long oldPrice, long newPrice);
}
