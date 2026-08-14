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

    private String listingCondition; // nullable — 상품 상태 키(시세 기록용)

    private long amount;

    @Indexed
    private String status;

    private Instant createdAt;

    private List<StatusChangeDocument> statusHistory;

    private PaymentMethodDocument paymentMethod; // nullable, 결제 승인 시 채워짐

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
            String listingCondition,
            long amount,
            String status,
            Instant createdAt,
            List<StatusChangeDocument> statusHistory,
            PaymentMethodDocument paymentMethod,
            SettlementDocument settlement,
            Long version) {
        this.id = id;
        this.listingId = listingId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.catalogSetNumber = catalogSetNumber;
        this.listingCondition = listingCondition;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.statusHistory = statusHistory;
        this.paymentMethod = paymentMethod;
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

    public String getListingCondition() {
        return listingCondition;
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

    public PaymentMethodDocument getPaymentMethod() {
        return paymentMethod;
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

    /** 결제수단 임베디드 문서. ({@code PaymentMethod}와 매핑) */
    public static class PaymentMethodDocument {

        private String type;
        private String provider; // nullable — 간편결제가 아니거나 사업자 불명

        protected PaymentMethodDocument() {
            // MongoDB 매핑용
        }

        public PaymentMethodDocument(String type, String provider) {
            this.type = type;
            this.provider = provider;
        }

        public String getType() {
            return type;
        }

        public String getProvider() {
            return provider;
        }
    }

    /** 정산 결과 임베디드 문서. ({@code Settlement}와 매핑) */
    public static class SettlementDocument {

        private String orderId;
        private String sellerId;
        private long grossAmount;
        private long fee;
        private long payout;
        private Double feeRate; // nullable — feeRate 도입 이전 레거시 문서
        private Instant settledAt;

        protected SettlementDocument() {
            // MongoDB 매핑용
        }

        public SettlementDocument(
                String orderId,
                String sellerId,
                long grossAmount,
                long fee,
                long payout,
                double feeRate,
                Instant settledAt) {
            this.orderId = orderId;
            this.sellerId = sellerId;
            this.grossAmount = grossAmount;
            this.fee = fee;
            this.payout = payout;
            this.feeRate = feeRate;
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

        /**
         * 정산에 적용된 수수료율. feeRate 도입 이전 문서는 값이 없으므로 당시 상수(5%)로 보정한다.
         * (ListingPersistenceAdapter가 레거시 필드를 보정하는 것과 같은 방식)
         */
        @SuppressWarnings("deprecation")
        public double getFeeRate() {
            return feeRate == null ? com.gole.api.order.domain.model.Settlement.PLATFORM_FEE_RATE : feeRate;
        }

        public Instant getSettledAt() {
            return settledAt;
        }
    }
}
