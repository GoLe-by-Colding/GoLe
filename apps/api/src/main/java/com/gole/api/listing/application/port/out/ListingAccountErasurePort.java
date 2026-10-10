package com.gole.api.listing.application.port.out;

import com.gole.api.listing.application.port.in.ListingAccountErasureUseCase.ListingErasure;

/** Outbound port: 회원 탈퇴 때 매물 기록의 차단 판정·파기를 저장소에서 한다. */
public interface ListingAccountErasurePort {

    boolean hasPublicContent(String accountId);

    ListingErasure erase(String accountId, String anonymousSubject);
}
