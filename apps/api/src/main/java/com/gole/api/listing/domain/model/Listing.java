package com.gole.api.listing.domain.model;

import com.gole.api.listing.domain.exception.ListingStateException;
import com.gole.api.listing.domain.exception.MissingPhotoException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 리스팅 애그리거트 루트. 생성 불변식과 상태 전이를 캡슐화한다. (요구사항 5)
 * 프레임워크에 의존하지 않는 순수 도메인.
 */
public final class Listing {

    private final String id;
    private final String sellerId;
    private final String title;
    private final String description;
    private final Money price;
    private final ItemCondition condition;
    private final List<String> photoUrls;
    private final String catalogSetNumber; // nullable
    private final Instant createdAt;
    private ListingStatus status;

    public Listing(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            List<String> photoUrls,
            String catalogSetNumber,
            ListingStatus status,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.sellerId = requireText(sellerId, "sellerId");
        this.title = requireText(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.price = Objects.requireNonNull(price, "price");
        this.condition = Objects.requireNonNull(condition, "condition");
        if (photoUrls == null || photoUrls.isEmpty()) {
            throw new MissingPhotoException(); // 요구사항 5.2
        }
        this.photoUrls = List.copyOf(photoUrls);
        this.catalogSetNumber = catalogSetNumber;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** 신규 리스팅: ACTIVE 상태로 생성. (요구사항 5.1) */
    public static Listing create(
            String id,
            String sellerId,
            String title,
            String description,
            Money price,
            ItemCondition condition,
            List<String> photoUrls,
            String catalogSetNumber,
            Instant createdAt) {
        return new Listing(
                id, sellerId, title, description, price, condition,
                photoUrls, catalogSetNumber, ListingStatus.ACTIVE, createdAt);
    }

    /** 판매 완료 처리. (요구사항 5.6) */
    public void markSold() {
        if (status == ListingStatus.DELETED) {
            throw new ListingStateException("LISTING_DELETED", "Deleted listing cannot be sold");
        }
        this.status = ListingStatus.SOLD;
    }

    /** 삭제. 진행 중 주문(RESERVED)이 있으면 거부. (요구사항 5.7, 5.8) */
    public void delete() {
        if (status == ListingStatus.RESERVED) {
            throw new ListingStateException(
                    "LISTING_ORDER_IN_PROGRESS", "Listing with an in-progress order cannot be deleted");
        }
        this.status = ListingStatus.DELETED;
    }

    /** 선점 해제(RESERVED → ACTIVE). 결제 실패/환불 시. 이미 활성이면 무시(멱등). */
    public void release() {
        if (status == ListingStatus.RESERVED) {
            this.status = ListingStatus.ACTIVE;
        }
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
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

    public Money getPrice() {
        return price;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public String getCatalogSetNumber() {
        return catalogSetNumber;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
