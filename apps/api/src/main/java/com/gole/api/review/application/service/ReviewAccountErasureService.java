package com.gole.api.review.application.service;

import com.gole.api.review.application.port.in.ReviewAccountErasureUseCase;
import com.gole.api.review.application.port.out.ReviewAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 후기 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class ReviewAccountErasureService implements ReviewAccountErasureUseCase {

    private final ReviewAccountErasurePort erasure;

    public ReviewAccountErasureService(ReviewAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public ReviewErasure erase(String accountId, String anonymousSubject) {
        return erasure.erase(accountId, anonymousSubject);
    }
}
