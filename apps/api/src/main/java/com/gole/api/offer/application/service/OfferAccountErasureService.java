package com.gole.api.offer.application.service;

import com.gole.api.offer.application.port.in.OfferAccountErasureUseCase;
import com.gole.api.offer.application.port.out.OfferAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 가격 제안 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class OfferAccountErasureService implements OfferAccountErasureUseCase {

    private final OfferAccountErasurePort erasure;

    public OfferAccountErasureService(OfferAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public OfferErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
