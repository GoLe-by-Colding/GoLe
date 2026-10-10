package com.gole.api.bid.application.port.out;

import com.gole.api.bid.application.port.in.BidAccountErasureUseCase.BidErasure;

/** Outbound port: 회원 탈퇴 때 구매 입찰 기록의 차단 판정·파기를 저장소에서 한다. */
public interface BidAccountErasurePort {

    BidErasure erase(String accountId, String anonymousSubject);
}
