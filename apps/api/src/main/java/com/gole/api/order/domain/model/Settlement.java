package com.gole.api.order.domain.model;

import java.time.Instant;

/**
 * 정산 결과(완료 주문 1건당 1개, 멱등). (요구사항 13.4, 13.5)
 * payout = grossAmount - 플랫폼 수수료.
 */
public record Settlement(String orderId, String sellerId, long grossAmount, long fee, long payout, Instant settledAt) {

    /** 기본 플랫폼 수수료율 5%. */
    public static final double PLATFORM_FEE_RATE = 0.05;

    public static Settlement compute(String orderId, String sellerId, long gross, Instant now) {
        long fee = Math.round(gross * PLATFORM_FEE_RATE);
        return new Settlement(orderId, sellerId, gross, fee, gross - fee, now);
    }
}
