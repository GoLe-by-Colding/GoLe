package com.gole.api.shipping.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 배송 MongoDB 영속 모델. 순수 도메인({@code Shipment})과 분리하고 매핑은
 * {@link ShipmentPersistenceAdapter}가 담당한다.
 *
 * <p>인덱스는 파이프라인 만료 후보 조회용이다(설계 P2). {@code shipments}는 새 컬렉션이라
 * 기존 인덱스와의 이름 충돌 위험이 없다(과거 {@code follows} 부팅 실패 전례 참고).
 */
@Document(collection = "shipments")
@CompoundIndexes({
    @CompoundIndex(name = "shipment_status_delivered_at_idx", def = "{'status': 1, 'deliveredAt': 1}"),
    @CompoundIndex(name = "shipment_status_registered_at_idx", def = "{'status': 1, 'registeredAt': 1}"),
    @CompoundIndex(name = "shipment_status_changed_at_idx", def = "{'status': 1, 'statusChangedAt': 1}")
})
public class ShipmentDocument {

    @Id
    private String id;

    @Indexed(unique = true, name = "shipment_order_id_uq")
    private String orderId;

    private String sellerId;
    private String buyerId;
    private String sellerPhone; // nullable — 숫자만 정규화 저장

    private String carrier;
    private String waybillNumber;
    private String status;
    private String rawStatus; // nullable — 택배사 원문 상태

    private Instant registeredAt;
    private Instant statusChangedAt;
    private Instant deliveredAt; // nullable
    private Instant lastTrackedAt; // nullable

    @Indexed(name = "shipment_unknown_since_idx")
    private Instant unknownSince; // nullable

    private List<WaybillChangeDocument> history;

    @Version
    private Long version;

    protected ShipmentDocument() {
        // MongoDB 매핑용
    }

    public ShipmentDocument(
            String id,
            String orderId,
            String sellerId,
            String buyerId,
            String sellerPhone,
            String carrier,
            String waybillNumber,
            String status,
            String rawStatus,
            Instant registeredAt,
            Instant statusChangedAt,
            Instant deliveredAt,
            Instant lastTrackedAt,
            Instant unknownSince,
            List<WaybillChangeDocument> history,
            Long version) {
        this.id = id;
        this.orderId = orderId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.sellerPhone = sellerPhone;
        this.carrier = carrier;
        this.waybillNumber = waybillNumber;
        this.status = status;
        this.rawStatus = rawStatus;
        this.registeredAt = registeredAt;
        this.statusChangedAt = statusChangedAt;
        this.deliveredAt = deliveredAt;
        this.lastTrackedAt = lastTrackedAt;
        this.unknownSince = unknownSince;
        this.history = history;
        this.version = version;
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

    public String getCarrier() {
        return carrier;
    }

    public String getWaybillNumber() {
        return waybillNumber;
    }

    public String getStatus() {
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

    public List<WaybillChangeDocument> getHistory() {
        return history;
    }

    public Long getVersion() {
        return version;
    }

    /** 운송장 교체 이력 임베디드 문서. */
    public static class WaybillChangeDocument {

        private String carrier;
        private String waybillNumber;
        private Instant replacedAt;

        protected WaybillChangeDocument() {
            // MongoDB 매핑용
        }

        public WaybillChangeDocument(String carrier, String waybillNumber, Instant replacedAt) {
            this.carrier = carrier;
            this.waybillNumber = waybillNumber;
            this.replacedAt = replacedAt;
        }

        public String getCarrier() {
            return carrier;
        }

        public String getWaybillNumber() {
            return waybillNumber;
        }

        public Instant getReplacedAt() {
            return replacedAt;
        }
    }
}
