package com.gole.api.collection.adapter.in.web;

import com.gole.api.collection.domain.model.CollectionItem;
import com.gole.api.collection.domain.model.CollectionValueSnapshot;
import com.gole.api.collection.domain.model.OwnershipStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class CollectionDtos {

    private CollectionDtos() {}

    public record AddItemRequest(
            @NotBlank String userId,
            @NotBlank String setNumber,
            @NotNull OwnershipStatus status) {}

    public record CollectionItemResponse(String id, String setNumber, String status, Instant createdAt) {

        public static CollectionItemResponse from(CollectionItem item) {
            return new CollectionItemResponse(
                    item.id(), item.setNumber(), item.status().name().toLowerCase(), item.createdAt());
        }
    }

    public record EstimateResponse(long ownedEstimatedValue) {}

    /** 자산 추이. 날짜 오름차순. (collection-value-history H3) */
    public record ValueHistoryResponse(List<ValuePointResponse> points) {

        public static ValueHistoryResponse from(List<CollectionValueSnapshot> snapshots) {
            return new ValueHistoryResponse(
                    snapshots.stream().map(ValuePointResponse::from).toList());
        }
    }

    /** @param date Asia/Seoul 기준 {@code yyyy-MM-dd} */
    public record ValuePointResponse(String date, long ownedValue, int ownedCount, int pricedCount) {

        static ValuePointResponse from(CollectionValueSnapshot snapshot) {
            return new ValuePointResponse(
                    snapshot.date().toString(), snapshot.ownedValue(), snapshot.ownedCount(), snapshot.pricedCount());
        }
    }
}
