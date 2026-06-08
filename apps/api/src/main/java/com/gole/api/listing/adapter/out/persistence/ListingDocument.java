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

    private List<String> photoUrls;

    private String catalogSetNumber; // nullable

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
            List<String> photoUrls,
            String catalogSetNumber,
            String status,
            Instant createdAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
        this.condition = condition;
        this.photoUrls = photoUrls;
        this.catalogSetNumber = catalogSetNumber;
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

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public String getCatalogSetNumber() {
        return catalogSetNumber;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
