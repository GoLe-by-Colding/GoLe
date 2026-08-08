package com.gole.api.order.adapter.out.settlement;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "settlements")
public class SettlementDocument {

    @Id
    private String orderId;

    @Indexed
    private String sellerId;

    private long grossAmount;
    private long fee;
    private long payout;
    private double feeRate;

    @Indexed
    private String status;

    private String paymentReference;
    private Instant createdAt;
    private Instant paidAt;

    @Version
    private Long version;

    protected SettlementDocument() {}

    SettlementDocument(
            String orderId,
            String sellerId,
            long grossAmount,
            long fee,
            long payout,
            double feeRate,
            String status,
            String paymentReference,
            Instant createdAt,
            Instant paidAt) {
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.grossAmount = grossAmount;
        this.fee = fee;
        this.payout = payout;
        this.feeRate = feeRate;
        this.status = status;
        this.paymentReference = paymentReference;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
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

    public double getFeeRate() {
        return feeRate;
    }

    public String getStatus() {
        return status;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
