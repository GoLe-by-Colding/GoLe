package com.gole.api.discovery.application.port.in;

import java.util.List;

/**
 * 다른 컨텍스트가 매물을 찜(위시리스트 {@code LISTING})한 사용자에게 소식을 전달할 때 쓰는 조회 포트.
 * 매물 가격 인하 알림의 수신자 해석에 쓴다. (listing-edit-and-bump E8)
 */
public interface ListListingWishersUseCase {

    List<String> wishersOf(String listingId);
}
