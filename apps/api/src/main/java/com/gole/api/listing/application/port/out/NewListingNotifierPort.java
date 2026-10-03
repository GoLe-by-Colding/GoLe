package com.gole.api.listing.application.port.out;

/** 새 매물이 등록된 뒤 셀러 팔로워·관심 세트 사용자에게 소식을 전달하는 포트. */
public interface NewListingNotifierPort {

    void notifyFollowers(String sellerId, String listingId, String title);

    /** 카탈로그 세트에 연결된 매물이면 그 세트의 위시리스트·희망 사용자에게 알린다. (set-watch-alerts W3) */
    void notifySetWatchers(String sellerId, String listingId, String title, String setNumber);
}
