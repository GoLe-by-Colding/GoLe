package com.gole.api.offer.application.port.out;

import com.gole.api.offer.application.port.in.OfferAccountErasureUseCase.OfferErasure;

/** Outbound port: 회원 탈퇴 때 가격 제안 기록의 차단 판정·파기를 저장소에서 한다. */
public interface OfferAccountErasurePort {

    OfferErasure erase(String accountId, String anonymousSubject);
}
