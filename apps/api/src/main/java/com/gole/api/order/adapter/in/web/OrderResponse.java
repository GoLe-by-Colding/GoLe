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
        Instant createdAt,
        List<StatusChange> history) {

    public record StatusChange(String status, Instant occurredAt) {
    }

    public static OrderResponse from(Order order) {
        List<StatusChange> history = order.getHistory().stream()
                .map(c -> new StatusChange(c.status().name().toLowerCase(), c.occurredAt()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getListingId(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getCatalogSetNumber(),
                order.getAmount(),
                order.getStatus().name().toLowerCase(),
                order.getCreatedAt(),
                history);
    }
}
