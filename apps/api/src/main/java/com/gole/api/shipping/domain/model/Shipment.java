package com.gole.api.shipping.domain.model;

import com.gole.api.shipping.domain.exception.ShipmentStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 배송 애그리거트. 주문 1건의 운송장과 배송 상태를 캡슐화한다. (shipping-and-fees B3)
 *
 * <p>주문의 하위 개념이 아니라 별도 애그리거트다 — 운송장은 주문과 1:1이지만
 * 생명주기(폴링·상태 전이·재조회)가 완전히 다르고, 외부 트래커 API 실패가
 * 주문 정합성을 오염시키면 안 된다.
 *
 * <p>불변식:
 * <ul>
 *   <li>{@code orderId}·{@code sellerId}·{@code buyerId}는 필수이며 변경 불가
 *   <li>운송장이 바뀌면 직전 값을 {@code history}에 보존한다 (R1.4)
 *   <li>상태는 {@code PENDING → IN_TRANSIT → DELIVERED} 단방향. 역행 금지 (외부 API 흔들림 방어)
 *   <li>{@code deliveredAt}은 {@code DELIVERED} 전이 시 1회만 기록
 * </ul>
 */
public final class Shipment {

    private final String id;
    private final String orderId;
    private final String sellerId;
    private final String buyerId;
    /** 판매자 CS 연락처(숫자만 정규화). 운송장 등록 시점에 수집한다. (R8.2) */
    private String sellerPhone;

    private Carrier carrier;
    private WaybillNumber waybill;
    private DeliveryStatus status;
    /** 택배사 원문 상태. 정규화 값과 별개로 보존한다. (R2.2) */
    private String rawStatus;

    private final Instant registeredAt;
    private Instant statusChangedAt;
    private Instant deliveredAt;
    private Instant lastTrackedAt;
    /** 트래커가 연속으로 UNKNOWN을 돌려주기 시작한 시각. 정상 조회되면 지운다. (R9 추적불가) */
    private Instant unknownSince;

    private final List<WaybillChange> history;
    private Long version;

    public Shipment(
            String id,
            String orderId,
            String sellerId,
            String buyerId,
            String sellerPhone,
            Carrier carrier,
            WaybillNumber waybill,
            DeliveryStatus status,
            String rawStatus,
            Instant registeredAt,
            Instant statusChangedAt,
            Instant deliveredAt,
            Instant lastTrackedAt,
            Instant unknownSince,
            List<WaybillChange> history,
            Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.sellerId = Objects.requireNonNull(sellerId, "sellerId");
        this.buyerId = Objects.requireNonNull(buyerId, "buyerId");
        this.sellerPhone = sellerPhone;
        this.carrier = Objects.requireNonNull(carrier, "carrier");
        this.waybill = Objects.requireNonNull(waybill, "waybill");
        this.status = Objects.requireNonNull(status, "status");
        this.rawStatus = rawStatus;
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.statusChangedAt = Objects.requireNonNull(statusChangedAt, "statusChangedAt");
        this.deliveredAt = deliveredAt;
        this.lastTrackedAt = lastTrackedAt;
        this.unknownSince = unknownSince;
        this.history = new ArrayList<>(history);
        this.version = version;
    }

    /** 신규 운송장 등록. */
    public static Shipment register(
            String id,
            String orderId,
            String sellerId,
            String buyerId,
            String sellerPhone,
            Carrier carrier,
            WaybillNumber waybill,
            Instant now) {
        return new Shipment(
                id,
                orderId,
                sellerId,
                buyerId,
                sellerPhone,
                carrier,
                waybill,
                DeliveryStatus.PENDING,
                null,
                now,
                now,
                null,
                null,
                null,
                new ArrayList<>(),
                null);
    }

    /**
     * 운송장 교체(오등록 정정). 직전 값을 이력에 보존하고 추적 상태를 처음으로 되돌린다. (R1.4)
     *
     * <p>배송 완료 뒤에는 교체할 수 없다 — 완료 사실이 자동 구매확정의 근거가 되므로
     * 사후 변경을 허용하면 정산 근거가 흔들린다.
     */
    public void replaceWaybill(Carrier newCarrier, WaybillNumber newWaybill, String newSellerPhone, Instant now) {
        if (status == DeliveryStatus.DELIVERED) {
            throw new ShipmentStateException("배송이 완료된 주문의 운송장은 변경할 수 없습니다");
        }
        history.add(new WaybillChange(carrier, waybill.value(), now));
        this.carrier = newCarrier;
        this.waybill = newWaybill;
        if (newSellerPhone != null) {
            this.sellerPhone = newSellerPhone;
        }
        this.status = DeliveryStatus.PENDING;
        this.rawStatus = null;
        this.statusChangedAt = now;
        this.unknownSince = null;
    }

    /**
     * 트래커 조회 결과를 반영한다. 역행은 무시하고 전진만 허용한다.
     *
     * @return {@code DELIVERED}로 <b>새로</b> 전이했으면 true (알림 트리거용)
     */
    public boolean applyTracking(DeliveryStatus reported, String reportedRawStatus, Instant now) {
        this.lastTrackedAt = now;
        if (reportedRawStatus != null && !reportedRawStatus.isBlank()) {
            this.rawStatus = reportedRawStatus;
        }
        if (reported == DeliveryStatus.UNKNOWN) {
            if (unknownSince == null) {
                unknownSince = now;
            }
            return false;
        }
        unknownSince = null;
        if (!reported.advancesFrom(status)) {
            return false;
        }
        boolean newlyDelivered = reported == DeliveryStatus.DELIVERED && deliveredAt == null;
        this.status = reported;
        this.statusChangedAt = now;
        if (newlyDelivered) {
            this.deliveredAt = now;
        }
        return newlyDelivered;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getSellerPhone() {
        return sellerPhone;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public WaybillNumber getWaybill() {
        return waybill;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getLastTrackedAt() {
        return lastTrackedAt;
    }

    public Instant getUnknownSince() {
        return unknownSince;
    }

    public List<WaybillChange> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public Long getVersion() {
        return version;
    }
}
