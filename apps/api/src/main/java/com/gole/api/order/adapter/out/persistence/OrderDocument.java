package com.gole.api.order.adapter.out.persistence;

import com.gole.api.order.domain.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 주문 MongoDB 영속 모델. 순수 도메인 모델({@code Order})과 분리되어 있으며
 * 매핑은 {@link OrderPersistenceAdapter}가 담당한다.
 *
 * <p>{@code @Version} 필드로 낙관적 락을 적용해 단일 낙찰/멱등 전이를 보장한다.
 * 상태 이력({@link StatusChangeDocument})과 정산({@link SettlementDocument})은 임베디드 문서로 저장한다.
 */
@Document(collection = "orders")
public class OrderDocument {

    @Id
    private String id;

    @Indexed
    private String listingId;

    @Indexed
    private String buyerId;

    @Indexed
    private String sellerId;

    private String catalogSetNumber; // nullable

    private long amount;

    @Indexed
    private String status;

    private Instant createdAt;

    private List<StatusChangeDocument> statusHistory;

    private SettlementDocument settlement; // nullable, 완료/정산 시 채워짐

    @Version
    private Long version;

    protected OrderDocument() {
        // MongoDB 매핑용
    }

    public OrderDocument(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            long amount,
            String status,
            Instant createdAt,
            List<StatusChangeDocument> statusHistory,
            SettlementDocument settlement,
            Long version) {
        this.id = id;
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.catalogSetNumber = catalogSetNumber;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.statusHistory = statusHistory;
        this.settlement = settlement;
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public String getListingId() {
        return listingId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getCatalogSetNumber() {
        return catalogSetNumber;
    }

    public long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<StatusChangeDocument> getStatusHistory() {
        return statusHistory;
    }

    public SettlementDocument getSettlement() {
        return settlement;
    }

    public Long getVersion() {
        return version;
    }

    /** 주문 상태 전이 이력 임베디드 문서. ({@code OrderStatusChange}와 매핑) */
    public static class StatusChangeDocument {

        private String status;
        private Instant occurredAt;

        protected StatusChangeDocument() {
            // MongoDB 매핑용
        }

        public StatusChangeDocument(String status, Instant occurredAt) {
            this.status = status;
            this.occurredAt = occurredAt;
        }

        public static StatusChangeDocument of(OrderStatus status, Instant occurredAt) {
            return new StatusChangeDocument(status.name(), occurredAt);
        }

        public String getStatus() {
            return status;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    /** 정산 결과 임베디드 문서. ({@code Settlement}와 매핑) */
    public static class SettlementDocument {

        private String orderId;
        private String sellerId;
        private long grossAmount;
        private long fee;
        private long payout;
        private Instant settledAt;

        protected SettlementDocument() {
            // MongoDB 매핑용
        }

        public SettlementDocument(
                String orderId, String sellerId, long grossAmount, long fee, long payout, Instant settledAt) {
            this.orderId = orderId;
            this.sellerId = sellerId;
            this.grossAmount = grossAmount;
            this.fee = fee;
            this.payout = payout;
            this.settledAt = settledAt;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getSellerId() {
            return sellerId;
        }

        public long getGrossAmount() {
            return grossAmount;
        }

        public long getFee() {
            return fee;
        }

        public long getPayout() {
            return payout;
        }

        public Instant getSettledAt() {
            return settledAt;
        }
    }
}
