package com.gole.api.listing.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
            @Size(max = 100) String category,
            @Size(max = 100) String interestTag) {}

    /**
     * 매물 수정 요청. 필드를 통째로 교체하며 검증은 등록({@link CreateListingRequest})과 같다. (E1)
     *
     * <p>{@code catalogSetNumber}·{@code category}·{@code sellerId}는 받지 않는다(E2). 등록 폼을
     * 그대로 공유하는 클라이언트가 보내도 실패하지 않도록 모르는 필드는 무시한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateListingRequest(
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
            @Size(max = 100) String interestTag) {}
}
