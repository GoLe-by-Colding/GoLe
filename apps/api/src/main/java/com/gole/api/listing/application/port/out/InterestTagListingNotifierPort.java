package com.gole.api.listing.application.port.out;

import com.gole.api.listing.domain.model.InterestTag;

/** 관심 테마가 지정된 새 매물의 알림톡 FANOUT을 요청하는 출력 포트. */
public interface InterestTagListingNotifierPort {

    void notifyInterestTagSubscribers(String sellerId, String listingId, String title, InterestTag tag);
}
