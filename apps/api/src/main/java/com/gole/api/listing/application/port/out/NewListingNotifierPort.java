package com.gole.api.listing.application.port.out;

/** 새 매물이 등록된 뒤 셀러를 팔로우한 사용자에게 소식을 전달하는 포트. */
public interface NewListingNotifierPort {

    void notifyFollowers(String sellerId, String listingId, String title);
}
