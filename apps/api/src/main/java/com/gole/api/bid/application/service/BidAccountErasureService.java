package com.gole.api.bid.application.service;

import com.gole.api.bid.application.port.in.BidAccountErasureUseCase;
import com.gole.api.bid.application.port.out.BidAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 구매 입찰 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class BidAccountErasureService implements BidAccountErasureUseCase {

    private final BidAccountErasurePort erasure;

    public BidAccountErasureService(BidAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public BidErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
