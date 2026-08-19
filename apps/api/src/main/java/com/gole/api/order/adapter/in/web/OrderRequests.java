package com.gole.api.order.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public final class OrderRequests {

    private OrderRequests() {}

    /**
     * @param buyerPhone 구매자 CS 연락처(R8.1). 형식 검증·정규화는 도메인 {@code PhoneNumber}가 한다.
     */
    public record PlaceOrderRequest(
            @NotBlank @jakarta.validation.constraints.Size(max = 100) String listingId,
            String buyerId,
            @jakarta.validation.constraints.Size(max = 20) String buyerPhone) {}

    public record OpenDisputeRequest(
            @NotBlank String reason, @jakarta.validation.constraints.Size(max = 1000) String detail) {}
}
