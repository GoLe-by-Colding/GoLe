package com.gole.api.order.application.port.out;

import com.gole.api.common.exception.DomainException;

/**
 * PG 원장은 조회됐지만 자동 승인·환불할 수 없어 운영자 확인이 필요한 결정적 오류.
 *
 * <p>네트워크 장애와 달리 같은 요청을 즉시 반복해도 해결되지 않으므로 503 재시도 경로와 분리한다.
 */
public class PaymentReviewRequiredException extends DomainException {

    public PaymentReviewRequiredException() {
        super("PAYMENT_REVIEW_REQUIRED", "결제 상태를 자동 처리할 수 없어 운영팀 확인이 필요합니다");
    }
}
