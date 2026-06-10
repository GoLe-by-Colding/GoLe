package com.gole.api.listing.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 리스팅 MongoDB 영속 모델. 순수 도메인 모델({@code Listing})과 분리되어 있으며
 * 매핑은 {@link ListingPersistenceAdapter}가 담당한다.
 *
 * <p>금액({@code Money})은 amount/currency 를 가지는 임베디드 값으로 저장한다.
 */
@Document(collection = "listings")
public class ListingDocument {

    @Id
    private String id;

    @Indexed
    private String sellerId;

    private String title;

    private String description;

    // 임베디드 금액 값 객체
    private long priceAmount;
    private String priceCurrency;

    private String condition;

    // 상태 고지(구성/박스/설명서/누락/하자). 레거시 문서는 null → 기본값 보정.
    private String completeness;
    private Boolean hasBox;
    private Boolean hasManual;
    private Boolean hasMissingParts;
    private String missingPartsNote;
    private String defectsNote;

    private List<String> photoUrls;

    private String catalogSetNumber; // nullable

    /** 매물 카테고리(set/parts/minifig/moc). 레거시 문서는 null → SET. */
    @Indexed
    private String category;

    @Indexed
    private String status;

    private Instant createdAt;

    protected ListingDocument() {
        // MongoDB 매핑용
    }

    public ListingDocument(
            String id,
            String sellerId,
            String title,
            String description,
            long priceAmount,
            String priceCurrency,
            String condition,
            String completeness,
            Boolean hasBox,
            Boolean hasManual,
            Boolean hasMissingParts,
            String missingPartsNote,
            String defectsNote,
            List<String> photoUrls,
            String catalogSetNumber,
            String category,
            String status,
            Instant createdAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
        this.condition = condition;
        this.completeness = completeness;
        this.hasBox = hasBox;
        this.hasManual = hasManual;
        this.hasMissingParts = hasMissingParts;
        this.missingPartsNote = missingPartsNote;
        this.defectsNote = defectsNote;
        this.photoUrls = photoUrls;
        this.catalogSetNumber = catalogSetNumber;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceAmount() {
        return priceAmount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public String getCondition() {
        return condition;
    }

    public String getCompleteness() {
        return completeness;
    }

    public Boolean getHasBox() {
        return hasBox;
    }

    public Boolean getHasManual() {
        return hasManual;
    }

    public Boolean getHasMissingParts() {
        return hasMissingParts;
    }

    public String getMissingPartsNote() {
        return missingPartsNote;
    }

    public String getDefectsNote() {
        return defectsNote;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public String getCatalogSetNumber() {
        return catalogSetNumber;
    }

    public String getCategory() {
        return category;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
