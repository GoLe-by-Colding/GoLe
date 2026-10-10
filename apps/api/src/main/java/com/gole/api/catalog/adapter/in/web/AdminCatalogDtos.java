package com.gole.api.catalog.adapter.in.web;

import com.gole.api.catalog.application.port.in.ListLegoSetsUseCase.LegoSetSummary;
import com.gole.api.catalog.domain.model.CatalogImagePath;
import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 관리자 카탈로그 API({@code /api/admin/catalog/sets})의 요청/응답 DTO. */
public final class AdminCatalogDtos {

    private AdminCatalogDtos() {}

    public record FeaturedRequest(boolean featured) {}

    public record CreateSetRequest(
            @NotBlank String setNumber,
            @NotBlank String name,
            @NotBlank String theme,
            @Min(0) int pieceCount,
            int releaseYear,
            @NotNull RetirementStatus retirementStatus,

            @Pattern(regexp = CatalogImagePath.REGEXP) String imageUrl,

            boolean featured) {}

    public record UpdateSetRequest(
            @NotBlank String name,
            @NotBlank String theme,
            @Min(0) int pieceCount,
            int releaseYear,
            @NotNull RetirementStatus retirementStatus,

            @Pattern(regexp = CatalogImagePath.REGEXP) String imageUrl,

            boolean featured) {}

    public record LegoSetResponse(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            String retirementStatus,
            String imageUrl,
            boolean featured) {

        public static LegoSetResponse from(LegoSetSummary summary) {
            return from(summary.set(), summary.featured());
        }

        public static LegoSetResponse from(LegoSet s, boolean featured) {
            return new LegoSetResponse(
                    s.getSetNumber(),
                    s.getName(),
                    s.getTheme(),
                    s.getPieceCount(),
                    s.getReleaseYear(),
                    s.getRetirementStatus().name(),
                    s.getImageUrl(),
                    featured);
        }
    }
}
