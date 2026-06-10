package com.gole.api.listing.adapter.in.web;

import com.gole.api.listing.domain.model.Completeness;
import com.gole.api.listing.domain.model.ItemCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ListingRequests {

    private ListingRequests() {
    }

    public record CreateListingRequest(
            @NotBlank String sellerId,
            @NotBlank String title,
            @NotNull String description,
            @PositiveOrZero long price,
            @NotNull ItemCondition condition,
            Completeness completeness,
            boolean hasBox,
            boolean hasManual,
            boolean hasMissingParts,
            @Size(max = 1000) String missingPartsNote,
            @Size(max = 1000) String defectsNote,
            @NotEmpty List<@NotBlank String> photoUrls,
            String catalogSetNumber,
            String category) {
    }
}
