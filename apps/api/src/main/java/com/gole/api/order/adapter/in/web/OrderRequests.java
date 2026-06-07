package com.gole.api.order.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public final class OrderRequests {

    private OrderRequests() {
    }

    public record PlaceOrderRequest(@NotBlank String listingId, @NotBlank String buyerId) {
    }
}
