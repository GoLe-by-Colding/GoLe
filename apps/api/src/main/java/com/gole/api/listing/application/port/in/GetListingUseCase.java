package com.gole.api.listing.application.port.in;

import com.gole.api.listing.domain.model.Listing;

public interface GetListingUseCase {

    /** 상태와 무관한 내부 조회. 소유권 확인·관리자 조치·주문 복구 흐름에서 사용한다. */
    Listing getById(String listingId);

    /** 공개 표면용 조회. 삭제·운영자 내림 매물은 존재하지 않는 것처럼 처리한다. */
    Listing getPublicById(String listingId);
}
