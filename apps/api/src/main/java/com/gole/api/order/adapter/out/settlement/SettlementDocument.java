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

    @Indexed(unique = true, sparse = true)
    private String paymentReference;

    private Instant createdAt;
    private Instant paidAt;
    private int payoutAttempts;
    private String payoutAttemptId;
    private String payoutOperatorId;
    private Instant payoutAttemptedAt;
    private Instant payoutNextAttemptAt;
    private String payoutError;

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
            Instant paidAt,
            int payoutAttempts,
            String payoutAttemptId,
            String payoutOperatorId,
            Instant payoutAttemptedAt,
            Instant payoutNextAttemptAt,
            String payoutError) {
        this(orderId, sellerId, grossAmount, fee, payout, feeRate, status, paymentReference, createdAt, paidAt);
        this.payoutAttempts = payoutAttempts;
        this.payoutAttemptId = payoutAttemptId;
        this.payoutOperatorId = payoutOperatorId;
        this.payoutAttemptedAt = payoutAttemptedAt;
        this.payoutNextAttemptAt = payoutNextAttemptAt;
        this.payoutError = payoutError;
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

    public int getPayoutAttempts() {
        return payoutAttempts;
    }

    public String getPayoutAttemptId() {
        return payoutAttemptId;
    }

    public String getPayoutOperatorId() {
        return payoutOperatorId;
    }

    public Instant getPayoutAttemptedAt() {
        return payoutAttemptedAt;
    }

    public Instant getPayoutNextAttemptAt() {
        return payoutNextAttemptAt;
    }

    public String getPayoutError() {
        return payoutError;
    }
}
