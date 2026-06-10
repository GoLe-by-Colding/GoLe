package com.gole.api.collection.adapter.in.web;

import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.OwnershipStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class CollectionDtos {

    private CollectionDtos() {}

    public record AddItemRequest(
            @NotBlank String userId, @NotBlank String setNumber, @NotNull OwnershipStatus status) {}

    public record CollectionItemResponse(String id, String setNumber, String status, Instant createdAt) {

        public static CollectionItemResponse from(CollectionItem item) {
            return new CollectionItemResponse(
                    item.id(), item.setNumber(), item.status().name().toLowerCase(), item.createdAt());
        }
    }

    public record EstimateResponse(long ownedEstimatedValue) {}
}
