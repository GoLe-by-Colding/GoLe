package com.gole.api.bid.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 입찰 영속 모델. 도메인 {@code Bid}와 분리하고 매핑은 어댑터가 맡는다.
 *
 * <p>인덱스 (buy-bids Design):
 *
 * <ul>
 *   <li>호가창·즉시 판매 후보 — 세트·상태·저장 상태로 좁힌 뒤 높은 가격, 먼저 건 순({@code placedAt})
 *   <li>내 입찰 — 입찰자·최신순
 *   <li>사용자·세트·상태당 저장 {@code ACTIVE} 하나 — 부분 유일. "있으면 갱신"(D3)을 동시 요청에서도 지킨다
 * </ul>
 *
 * <p>동가 순서를 스펙의 {@code createdAt}이 아니라 {@code placedAt}(마지막으로 가격을 건 시각)으로 잡았다.
 * 가격을 올려 다시 건 입찰이 처음부터 그 가격이었던 입찰보다 앞서면 안 되기 때문이다.
 */
@Document(collection = "bids")
@CompoundIndexes({
    @CompoundIndex(
            name = "bid_book_idx",
            def = "{'setNumber': 1, 'condition': 1, 'status': 1, 'price': -1, 'placedAt': 1}"),
    @CompoundIndex(name = "bid_bidder_created_idx", def = "{'bidderId': 1, 'createdAt': -1}"),
    @CompoundIndex(
            name = "bid_filled_listing_idx",
            def = "{'filledListingId': 1}",
            partialFilter = "{'status': 'FILLED'}"),
    @CompoundIndex(
            name = "uq_bid_active_bidder_set_condition",
            def = "{'bidderId': 1, 'setNumber': 1, 'condition': 1}",
            unique = true,
            partialFilter = "{'status': 'ACTIVE'}")
})
public class BidDocument {

    @Id
    private String id;

    private String bidderId;
    private String setNumber;
    /** 상태 키(new_sealed 등). */
    private String condition;

    private long price;
    private int durationDays;
    private String status;
    private Instant createdAt;
    private Instant placedAt;
    private Instant expiresAt;
    private Instant closedAt;
    private String filledListingId;
    private String offerId;

    protected BidDocument() {
        // MongoDB 매핑용
    }

    BidDocument(
            String id,
            String bidderId,
            String setNumber,
            String condition,
            long price,
            int durationDays,
            String status,
            Instant createdAt,
            Instant placedAt,
            Instant expiresAt,
            Instant closedAt,
            String filledListingId,
            String offerId) {
        this.id = id;
        this.bidderId = bidderId;
        this.setNumber = setNumber;
        this.condition = condition;
        this.price = price;
        this.durationDays = durationDays;
        this.status = status;
        this.createdAt = createdAt;
        this.placedAt = placedAt;
        this.expiresAt = expiresAt;
        this.closedAt = closedAt;
        this.filledListingId = filledListingId;
        this.offerId = offerId;
    }

    public String getId() {
        return id;
    }

    public String getBidderId() {
        return bidderId;
    }

    public String getSetNumber() {
        return setNumber;
    }

    public String getCondition() {
        return condition;
    }

    public long getPrice() {
        return price;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getFilledListingId() {
        return filledListingId;
    }

    public String getOfferId() {
        return offerId;
    }
}
