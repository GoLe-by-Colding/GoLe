package com.gole.api.catalog.adapter.out.persistence;

import com.gole.api.catalog.domain.model.LegoSet;
import com.gole.api.catalog.domain.model.RetirementStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * MongoDB 영속성 문서. 도메인 모델(LegoSet)과 분리된 별도 타입으로,
 * 도메인 ↔ 문서 매핑은 PersistenceAdapter가 담당한다.
 */
@Document(collection = "lego_sets")
public class LegoSetDocument {

    @Id
    private String setNumber;

    private String name;
    private String theme;
    private int pieceCount;
    private int releaseYear;
    private RetirementStatus retirementStatus;
    private String imageUrl;

    @Field("featured")
    private boolean featured;

    protected LegoSetDocument() {
        // MongoDB(스프링 데이터) 역직렬화용 기본 생성자
    }

    public LegoSetDocument(
            String setNumber,
            String name,
            String theme,
            int pieceCount,
            int releaseYear,
            RetirementStatus retirementStatus,
            String imageUrl,
            boolean featured) {
        this.setNumber = setNumber;
        this.name = name;
        this.theme = theme;
        this.pieceCount = pieceCount;
        this.releaseYear = releaseYear;
        this.retirementStatus = retirementStatus;
        this.imageUrl = imageUrl;
        this.featured = featured;
    }

    /**
     * 도메인 모델 → 문서.
     */
    public static LegoSetDocument fromDomain(LegoSet set, boolean featured) {
        return new LegoSetDocument(
                set.getSetNumber(),
                set.getName(),
                set.getTheme(),
                set.getPieceCount(),
                set.getReleaseYear(),
                set.getRetirementStatus(),
                set.getImageUrl(),
                featured);
    }

    /**
     * 문서 → 도메인 모델.
     */
    public LegoSet toDomain() {
        return new LegoSet(setNumber, name, theme, pieceCount, releaseYear, retirementStatus, imageUrl);
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

    public boolean isFeatured() {
        return featured;
    }
}
