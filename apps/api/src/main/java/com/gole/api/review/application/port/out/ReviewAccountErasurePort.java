package com.gole.api.review.application.port.out;

import com.gole.api.review.application.port.in.ReviewAccountErasureUseCase.ReviewErasure;

/** Outbound port: 회원 탈퇴 때 후기 기록의 차단 판정·파기를 저장소에서 한다. */
public interface ReviewAccountErasurePort {

    ReviewErasure erase(String accountId, String anonymousSubject);
}
