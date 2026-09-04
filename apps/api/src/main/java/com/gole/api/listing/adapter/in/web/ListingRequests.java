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

    private ListingRequests() {}

    public record CreateListingRequest(
            String sellerId,
            @NotBlank @Size(max = 120) String title,
            @NotNull @Size(max = 5000) String description,
            @PositiveOrZero long price,
            @NotNull ItemCondition condition,
            Completeness completeness,
            boolean hasBox,
            boolean hasManual,
            boolean hasMissingParts,
            @Size(max = 1000) String missingPartsNote,
            @Size(max = 1000) String defectsNote,
            @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 80) String> photoKeys,
            @Size(max = 100) String catalogSetNumber,
            @Size(max = 100) String category) {}
}
