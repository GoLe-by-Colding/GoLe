package com.gole.api.order.domain.model;

import com.gole.api.order.domain.exception.OrderStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 주문 애그리거트 루트. 상태 전이와 이력(감사)을 캡슐화한다. (요구사항 13)
 * 낙관적 락 버전은 영속성 계층(@Version)과 왕복하기 위해 보관한다.
 */
public final class Order {

    private final String id;
    private final String listingId;
    private final String buyerId;
    private final String sellerId;
    private final String catalogSetNumber; // nullable
    /** 상품 상태 키(new_sealed 등). 시세 condition 기록에 사용. nullable(레거시/미지정). */
    private final String listingCondition;

    private final long amount;
    private final Instant createdAt;
    private final List<OrderStatusChange> history;
    private OrderStatus status;

    /**
     * 결제수단. 결제 승인 시점에 PG가 알려준 값으로 채워지며, 승인 전이거나 결제수단 기록
     * 도입 이전 주문이면 null이다. (요구사항 13.2)
     */
    private PaymentMethod paymentMethod;

    /** 정산 전표. 완료 시점에 확정되며 그 전에는 null이다. (요구사항 13.4) */
    private Settlement settlement;

    private Long version;

    public Order(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            OrderStatus status,
            Instant createdAt,
            List<OrderStatusChange> history,
            PaymentMethod paymentMethod,
            Settlement settlement,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.buyerId = Objects.requireNonNull(buyerId, "buyerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.catalogSetNumber = catalogSetNumber;
        this.listingCondition = listingCondition;
        this.amount = amount;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.history = new ArrayList<>(history);
        this.paymentMethod = paymentMethod;
        this.settlement = settlement;
        this.version = version;
    }

    /** 하위호환 생성자(결제수단 기록 도입 이전). */
    public Order(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            OrderStatus status,
            Instant createdAt,
            List<OrderStatusChange> history,
            Settlement settlement,
            Long version) {
        this(
                id,
                listingId,
                buyerId,
                sellerId,
                catalogSetNumber,
                listingCondition,
                amount,
                status,
                createdAt,
                history,
                null,
                settlement,
                version);
    }

    /** 하위호환 생성자(정산 전표 도입 이전). */
    public Order(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            OrderStatus status,
            Instant createdAt,
            List<OrderStatusChange> history,
            Long version) {
        this(
                id,
                listingId,
                buyerId,
                sellerId,
                catalogSetNumber,
                listingCondition,
                amount,
                status,
                createdAt,
                history,
                null,
                null,
                version);
    }

    /** 하위호환 생성자(레거시/테스트). */
    public Order(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            long amount,
            OrderStatus status,
            Instant createdAt,
            List<OrderStatusChange> history,
            Long version) {
        this(id, listingId, buyerId, sellerId, catalogSetNumber, null, amount, status, createdAt, history, version);
    }

    /** 신규 주문: 결제 대기 상태로 생성. (요구사항 7.1) */
    public static Order place(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            Instant now) {
        List<OrderStatusChange> history = new ArrayList<>();
        history.add(new OrderStatusChange(OrderStatus.PAYMENT_PENDING, now));
        return new Order(
                id,
                listingId,
                buyerId,
                sellerId,
                catalogSetNumber,
                listingCondition,
                amount,
                OrderStatus.PAYMENT_PENDING,
                now,
                history,
                null);
    }

    /**
     * 결제 승인 → 자금 보유. 승인 시 PG가 알려준 결제수단을 함께 새긴다. (요구사항 7.2, 13.2)
     *
     * <p>결제수단은 승인 응답에만 실려 오는 사실이라 이 시점을 놓치면 되찾을 곳이 없다.
     * PG가 알려주지 않았으면 null을 받아 그대로 비워 둔다 — 모른다는 것도 기록이다.
     */
    public void confirmFundsHeld(Instant now, PaymentMethod paymentMethod) {
        requireStatus(OrderStatus.PAYMENT_PENDING, "funds-held");
        this.paymentMethod = paymentMethod;
        transitionTo(OrderStatus.FUNDS_HELD, now);
    }

    /** 결제 실패. (요구사항 13.3) */
    public void failPayment(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING, "payment-failed");
        transitionTo(OrderStatus.PAYMENT_FAILED, now);
    }

    /** 구매 확정 → 완료(정산 권한). (요구사항 7.4, 13.4) */
    public void complete(Instant now) {
        requireStatus(OrderStatus.FUNDS_HELD, "completed");
        transitionTo(OrderStatus.COMPLETED, now);
    }

    /** 환불. funds-held 에서만 가능. (요구사항 13.6) */
    public void refund(Instant now) {
        requireStatus(OrderStatus.FUNDS_HELD, "refunded");
        transitionTo(OrderStatus.REFUNDED, now);
    }

    /**
     * 정산 전표를 확정한다. 완료 주문에만, 단 한 번만 가능하다. (요구사항 13.5 exactly-once)
     *
     * <p>상태 전이({@code FUNDS_HELD → COMPLETED})가 이미 재실행을 막지만, 정산은 돈이 걸린
     * 계산이라 애그리거트에서도 이중 부착을 거부한다. 상태 머신 하나에만 의존하면 나중에
     * 재정산·수동 보정 경로가 생겼을 때 조용히 두 번 계산된다.
     */
    public void attachSettlement(Settlement settlement) {
        requireStatus(OrderStatus.COMPLETED, "settlement");
        if (this.settlement != null) {
            throw new OrderStateException("Settlement already attached for order " + id);
        }
        this.settlement = Objects.requireNonNull(settlement, "settlement");
    }

    private void requireStatus(OrderStatus expected, String target) {
        if (status != expected) {
            throw new OrderStateException("Cannot transition to " + target + " from " + status);
        }
    }

    private void transitionTo(OrderStatus next, Instant now) {
        this.status = next;
        this.history.add(new OrderStatusChange(next, now));
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

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderStatusChange> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** 결제수단. 결제 승인 전이거나 PG가 알려주지 않았으면 null. */
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    /** 정산 전표. 완료 전이거나 정산 도입 이전 레거시 주문이면 null. */
    public Settlement getSettlement() {
        return settlement;
    }

    public Long getVersion() {
        return version;
    }
}
