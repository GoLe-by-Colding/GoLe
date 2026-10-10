package com.gole.api.listing.application.service;

import com.gole.api.listing.application.port.in.ListingAccountErasureUseCase;
import com.gole.api.listing.application.port.out.ListingAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 매물 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class ListingAccountErasureService implements ListingAccountErasureUseCase {

    private final ListingAccountErasurePort erasure;

    public ListingAccountErasureService(ListingAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public boolean hasPublicContent(String accountId) {
        return erasure.hasPublicContent(accountId);
    }

    @Override
    public ListingErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
