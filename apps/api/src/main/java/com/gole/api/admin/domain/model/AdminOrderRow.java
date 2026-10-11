package com.gole.api.admin.domain.model;

import java.time.Instant;

/** {@code paymentMethod}는 결제 승인 전 주문과 결제수단 도입 이전 주문에서 null이다. */
public record AdminOrderRow(
        String id,
        String status,
        long amount,
        String buyerId,
        String sellerId,
        String catalogSetNumber,
        AdminPaymentMethodView paymentMethod,
        Instant createdAt) {}
