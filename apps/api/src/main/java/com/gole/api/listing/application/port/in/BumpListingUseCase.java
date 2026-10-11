package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;
import java.time.Duration;

/**
 * Inbound port: 매물 끌올. (listing-edit-and-bump B1~B4)
 *
 * <p>정렬 키({@code listedAt})를 지금으로 올린다. 쿨다운 안이면 429, 판매 중이 아니면 409,
 * 소유자가 아니면 403. 알림은 보내지 않는다.
 */
public interface BumpListingUseCase {

    Listing bump(String listingId, String sellerId);

    /** 끌올 쿨다운({@code gole.listing.bump.cooldown}). 응답의 {@code bumpAvailableAt} 계산에 쓴다. */
    Duration bumpCooldown();
}
