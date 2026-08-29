package com.gole.api.order.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 지급대행 스케줄러가 정산 원장을 원자적으로 선점하고 결과를 기록하는 포트. */
public interface AutomaticSettlementPort {

    /** 외부 결과를 모르는 채 재시도 상한에 도달한 선점을 지급 확인 필요 상태로 잠근다. */
    void blockExhaustedClaims(Instant now, Duration staleAfter, int maxAttempts);

    /** 운영자 ID가 붙은 수동 지급 선점은 자동 실행기가 회수하지 않는다. */
    Optional<Candidate> claimNext(Instant now, Duration holdback, Duration staleAfter, String attemptId);

    void markPaid(String orderId, String attemptId, String paymentReference, Instant paidAt);

    void markFailed(String orderId, String attemptId, String error, Instant failedAt, Duration retryAfter);

    void markBlocked(String orderId, String attemptId, String reason, Instant blockedAt);

    /** {@code attemptNumber}는 이번 선점을 포함한 누적 외부 지급 시도 번호다(1부터 시작). */
    record Candidate(String orderId, String sellerId, long payout, String attemptId, int attemptNumber) {}
}
