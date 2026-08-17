package com.gole.api.order.adapter.in.web;

import com.gole.api.order.domain.model.Order;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String listingId,
        String buyerId,
        String sellerId,
        String catalogSetNumber,
        long amount,
        String status,
        PaymentMethodResponse paymentMethod,
        Instant createdAt,
        List<StatusChange> history) {

    public record StatusChange(String status, Instant occurredAt) {}

    /**
     * 결제수단. 결제 승인 전에는 null이다.
     *
     * <p>{@code status}와 달리 대문자 열거형 이름을 그대로 쓴다. 같은 개념을 관리자 API와
     * 구매자 API가 다른 표기로 내보내면 프론트에 매핑 테이블이 두 벌 생긴다.
     *
     * @param type CARD·EASY_PAY 등
     * @param provider 간편결제 사업자(KAKAOPAY 등). 해당 없으면 null.
     */
    public record PaymentMethodResponse(String type, String provider) {}

    public static OrderResponse from(Order order) {
        List<StatusChange> history = order.getHistory().stream()
                .map(c -> new StatusChange(c.status().name().toLowerCase(), c.occurredAt()))
                .toList();
        var method = order.getPaymentMethod();
        return new OrderResponse(
                order.getId(),
                order.getListingId(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getCatalogSetNumber(),
                order.getAmount(),
                order.getStatus().name().toLowerCase(),
                method == null ? null : new PaymentMethodResponse(method.type().name(), method.provider()),
                order.getCreatedAt(),
                history);
    }
}
