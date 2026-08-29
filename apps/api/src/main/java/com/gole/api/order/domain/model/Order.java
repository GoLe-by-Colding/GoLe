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
    /** 결제 승인 시점에 PG가 알려준 결제수단. 결제 전·레거시 주문은 null. */
    private PaymentMethod paymentMethod;
    /** 구매자 CS 연락처(숫자만 정규화). 미수집(레거시) 주문은 null. (shipping-and-fees R8.1) */
    private final PhoneNumber buyerPhone;

    // 분쟁(R4). DISPUTED 진입 시 채워지고 판정 후에도 기록으로 남는다.
    private DisputeReason disputeReason;
    private String disputeDetail;
    private Instant disputeOpenedAt;
    /**
     * 판매자가 최초 운송장 등록을 선점한 시각.
     *
     * <p>배송 문서와 별개로 주문 버전에 함께 기록해, 미발송 환불 시작과 운송장 등록이
     * 서로 다른 컬렉션에서 동시에 성공하지 못하게 하는 동시성 펜스다.
     */
    private Instant shipmentRegisteredAt;

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
            PaymentMethod paymentMethod,
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
                paymentMethod,
                null,
                null,
                null,
                null,
                createdAt,
                history,
                version);
    }

    /** 정식 생성자(연락처·분쟁 필드 포함). 영속성 어댑터가 사용한다. */
    public Order(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PhoneNumber buyerPhone,
            DisputeReason disputeReason,
            String disputeDetail,
            Instant disputeOpenedAt,
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
                paymentMethod,
                buyerPhone,
                disputeReason,
                disputeDetail,
                disputeOpenedAt,
                null,
                createdAt,
                history,
                version);
    }

    /** 정식 생성자(배송 등록 동시성 펜스 포함). 영속성 어댑터가 사용한다. */
    public Order(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PhoneNumber buyerPhone,
            DisputeReason disputeReason,
            String disputeDetail,
            Instant disputeOpenedAt,
            Instant shipmentRegisteredAt,
            Instant createdAt,
            List<OrderStatusChange> history,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.listingId = Objects.requireNonNull(listingId, "listingId");
        this.buyerId = Objects.requireNonNull(buyerId, "buyerId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.catalogSetNumber = catalogSetNumber;
        this.listingCondition = listingCondition;
        this.amount = amount;
        this.status = Objects.requireNonNull(status, "status");
        this.paymentMethod = paymentMethod;
        this.buyerPhone = buyerPhone;
        this.disputeReason = disputeReason;
        this.disputeDetail = disputeDetail;
        this.disputeOpenedAt = disputeOpenedAt;
        this.shipmentRegisteredAt = shipmentRegisteredAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.history = new ArrayList<>(history);
        this.version = version;
    }

    /** 하위호환 생성자(결제수단 도입 이전). */
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
                null,
                createdAt,
                history,
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
        return place(id, listingId, buyerId, sellerId, catalogSetNumber, listingCondition, amount, null, now);
    }

    /** 신규 주문(구매자 CS 연락처 포함). (요구사항 7.1, shipping-and-fees R8.1) */
    public static Order place(
            String id,
            String listingId,
            String buyerId,
            String sellerId,
            String catalogSetNumber,
            String listingCondition,
            long amount,
            PhoneNumber buyerPhone,
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
                null,
                buyerPhone,
                null,
                null,
                null,
                now,
                history,
                null);
    }

    /**
     * 결제 승인 → 자금 보유. (요구사항 7.2, 13.2)
     *
     * <p>결제수단을 <b>이 전이에서만</b> 기록한다. 승인된 PG 원장에만 실려오는 사실이라
     * 나중에 되찾을 곳이 없고, 한 번 기록된 뒤에는 바뀌지 않는다.
     *
     * @param paymentMethod 확인된 결제수단. PG가 알려주지 않았으면 {@link PaymentMethod#UNKNOWN}.
     */
    public void confirmFundsHeld(Instant now, PaymentMethod paymentMethod) {
        requirePaymentDecisionStatus("funds-held");
        this.paymentMethod = paymentMethod == null ? PaymentMethod.UNKNOWN : paymentMethod;
        transitionTo(OrderStatus.FUNDS_HELD, now);
    }

    /** 결제수단을 알 수 없는 자금 보유 전이. */
    public void confirmFundsHeld(Instant now) {
        confirmFundsHeld(now, PaymentMethod.UNKNOWN);
    }

    /** 결제 실패. (요구사항 13.3) */
    public void failPayment(Instant now) {
        requirePaymentDecisionStatus("payment-failed");
        transitionTo(OrderStatus.PAYMENT_FAILED, now);
    }

    /** 금액 불일치·알 수 없는 PG 상태를 자동 실패 처리하지 않고 운영 검토함으로 보낸다. */
    public void flagPaymentReview(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING, "payment-review");
        transitionTo(OrderStatus.PAYMENT_REVIEW, now);
    }

    /**
     * 구매 확정 → 완료(정산 권한). (요구사항 7.4, 13.4)
     *
     * <p>{@code DISPUTED}에서도 가능하다 — 분쟁 판정이 "거래 완료"로 나거나(R4.4)
     * 구매자가 분쟁을 접고 직접 확정하는 경우다.
     */
    public void complete(Instant now) {
        if (status != OrderStatus.FUNDS_HELD && status != OrderStatus.DISPUTED) {
            throw new OrderStateException("Cannot transition to completed from " + status);
        }
        transitionTo(OrderStatus.COMPLETED, now);
    }

    /**
     * 분쟁 제기. (shipping-and-fees R4.1)
     *
     * <p>{@code FUNDS_HELD}에서만 진입한다 — 이미 완료·환불된 주문은 분쟁 대상이 아니다.
     * 자동 구매확정 후보 조회가 {@code status == FUNDS_HELD}이므로 이 전이만으로
     * 타이머 정지(R4.2)가 성립한다.
     */
    public void openDispute(DisputeReason reason, String detail, Instant now) {
        requireStatus(OrderStatus.FUNDS_HELD, "disputed");
        this.disputeReason = Objects.requireNonNull(reason, "reason");
        this.disputeDetail = detail;
        this.disputeOpenedAt = now;
        transitionTo(OrderStatus.DISPUTED, now);
    }

    /** 비동기 환불 접수. funds-held 또는 분쟁 판정(환불)에서 가능. */
    public void requestRefund(Instant now) {
        if (status != OrderStatus.FUNDS_HELD && status != OrderStatus.DISPUTED) {
            throw new OrderStateException("Cannot transition to refund-pending from " + status);
        }
        if (status == OrderStatus.FUNDS_HELD && shipmentRegisteredAt != null) {
            throw new OrderStateException("Cannot refund an order after shipment registration");
        }
        transitionTo(OrderStatus.REFUND_PENDING, now);
    }

    /**
     * 최초 운송장 등록을 주문 버전으로 선점한다.
     *
     * <p>같은 주문의 운송장 교체는 멱등으로 허용하지만, 환불이 먼저 시작됐다면 상태 검사에서
     * 거부된다. 이 메서드를 배송 문서 저장보다 먼저 커밋해야 미발송 환불과 교차 컬렉션 경쟁이
     * 생기지 않는다.
     *
     * @return 최초 등록 펜스를 새로 기록했으면 {@code true}
     */
    public boolean registerShipment(Instant now) {
        requireStatus(OrderStatus.FUNDS_HELD, "shipment-registered");
        if (shipmentRegisteredAt != null) {
            return false;
        }
        shipmentRegisteredAt = Objects.requireNonNull(now, "now");
        return true;
    }

    /** PG에서 확인된 환불 완료. 재전송 웹훅을 위해 멱등이다. (요구사항 13.6) */
    public void refund(Instant now) {
        if (status == OrderStatus.REFUNDED) {
            return;
        }
        if (status != OrderStatus.REFUND_PENDING) {
            throw new OrderStateException("Cannot transition to refunded from " + status);
        }
        transitionTo(OrderStatus.REFUNDED, now);
    }

    private void requireStatus(OrderStatus expected, String target) {
        if (status != expected) {
            throw new OrderStateException("Cannot transition to " + target + " from " + status);
        }
    }

    private void requirePaymentDecisionStatus(String target) {
        if (status != OrderStatus.PAYMENT_PENDING && status != OrderStatus.PAYMENT_REVIEW) {
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

    /** 결제 승인 시점에 확인된 결제수단. 결제 전이거나 결제수단 도입 이전 주문이면 null. */
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PhoneNumber getBuyerPhone() {
        return buyerPhone;
    }

    public DisputeReason getDisputeReason() {
        return disputeReason;
    }

    public String getDisputeDetail() {
        return disputeDetail;
    }

    public Instant getDisputeOpenedAt() {
        return disputeOpenedAt;
    }

    public Instant getShipmentRegisteredAt() {
        return shipmentRegisteredAt;
    }

    /** 마지막 상태 전이 시각. 파이프라인 타임아웃 판정의 기준이다. (R9) */
    public Instant getStatusChangedAt() {
        return history.isEmpty() ? createdAt : history.get(history.size() - 1).occurredAt();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderStatusChange> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public Long getVersion() {
        return version;
    }
}
