package com.gole.api.admin.adapter.in.web;

import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record OverviewResponse(Map<String, Long> counts) {
    }

    public record CreateSetRequest(
            @NotBlank String setNumber,
            @NotBlank String name,
            @NotBlank String theme,
            @Min(0) int pieceCount,
            int releaseYear,
            @NotNull RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {
    }

    public record LegoSetResponse(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            String retirementStatus,
            String imageUrl) {

        public static LegoSetResponse from(LegoSet s) {
            return new LegoSetResponse(
                    s.getSetNumber(),
                    s.getName(),
                    s.getTheme(),
                    s.getPieceCount(),
                    s.getReleaseYear(),
                    s.getRetirementStatus().name(),
                    s.getImageUrl());
        }
    }
}
