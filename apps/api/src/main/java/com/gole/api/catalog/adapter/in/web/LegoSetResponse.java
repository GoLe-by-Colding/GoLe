package com.gole.api.catalog.adapter.in.web;

import com.gole.api.catalog.domain.model.LegoSet;

/**
 * 웹 응답 DTO. 프론트엔드 entities/lego-set 의 LegoSet 타입과 형태가 일치한다.
 */
public record LegoSetResponse(
        String setNumber,
        String name,
        String theme,
        int pieceCount,
        int releaseYear,
        String retirementStatus,
        String imageUrl) {

    public static LegoSetResponse from(LegoSet set) {
        return new LegoSetResponse(
                set.getSetNumber(),
                set.getName(),
                set.getTheme(),
                set.getPieceCount(),
                set.getReleaseYear(),
                set.getRetirementStatus().name().toLowerCase(),
                set.getImageUrl());
    }
}
