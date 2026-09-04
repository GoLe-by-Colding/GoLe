package com.gole.api.catalog.domain.model;

import java.util.Objects;

/**
 * LEGO 카탈로그 세트 도메인 엔티티 (요구사항 4).
 * 프레임워크/영속성에 의존하지 않는 순수 도메인 모델.
 * 불변식(invariant)을 생성 시점에 강제한다.
 */
public final class LegoSet {

    private final String setNumber;
    private final String name;
    private final String theme;
    private final int pieceCount;
    private final int releaseYear;
    private final RetirementStatus retirementStatus;
    private final String imageUrl;

    public LegoSet(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            RetirementStatus retirementStatus,
            String imageUrl) {
        this.setNumber = requireText(setNumber, "setNumber");
        this.name = requireText(name, "name");
        this.theme = requireText(theme, "theme");
        if (pieceCount < 0) {
            throw new IllegalArgumentException("pieceCount must be >= 0");
        }
        this.pieceCount = pieceCount;
        this.releaseYear = releaseYear;
        this.retirementStatus = Objects.requireNonNull(retirementStatus, "retirementStatus");
        // 레거시 외부 URL은 도메인에 진입하는 즉시 격리해 응답/브라우저까지 전달하지 않는다.
        this.imageUrl = CatalogImagePath.quarantineUnsafeStoredValue(imageUrl);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public boolean isRetired() {
        return retirementStatus == RetirementStatus.RETIRED;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getName() {
        return name;
    }

    public String getTheme() {
        return theme;
    }

    public int getPieceCount() {
        return pieceCount;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public RetirementStatus getRetirementStatus() {
        return retirementStatus;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
