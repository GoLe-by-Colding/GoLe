package com.gole.api.listing.application.port.out;

import java.util.List;

/** Outbound port: 매물 사진의 미디어 수명주기(참조 교체·폐기). */
public interface ListingPhotoPort {

    /** 요청 목록이 매물의 전체 사진이다. 빠진 기존 사진 참조는 즉시 폐기하고, 매물 사진은 공개로 둔다. */
    void replacePhotos(String sellerId, String listingId, List<String> photoKeys);

    void revokePhotos(String listingId);
}
