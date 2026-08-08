package com.gole.api.order.domain.model;

import java.time.Instant;

/**
 * 정산 결과(완료 주문 1건당 1개, 멱등). (요구사항 13.4, 13.5)
 * payout = grossAmount - 플랫폼 수수료.
 *
 * <p>{@code feeRate}는 계산에 실제로 쓰인 요율이다. 나중에 정책이 바뀌어도 과거 정산을
 * 그대로 재현할 수 있어야 하므로 결과와 함께 보존한다. (shipping-and-fees R5.2)
 */
public record Settlement(
        String orderId, String sellerId, long grossAmount, long fee, long payout, double feeRate, Instant settledAt) {

    /**
     * 기본 플랫폼 수수료율 5%.
     *
     * @deprecated 정책은 {@link FeePolicy}로 외부화됐다. 이 상수는 설정 기본값의 출처로만 남긴다.
     *     새 코드에서 직접 참조하지 말 것. (shipping-and-fees R5.1)
     */
    @Deprecated
    public static final double PLATFORM_FEE_RATE = 0.05;

    /** 정책을 적용해 정산을 계산한다. */
    public static Settlement compute(String orderId, String sellerId, long gross, FeePolicy policy, Instant now) {
        long fee = policy.feeFor(gross);
        return new Settlement(orderId, sellerId, gross, fee, gross - fee, policy.rate(), now);
    }
}
