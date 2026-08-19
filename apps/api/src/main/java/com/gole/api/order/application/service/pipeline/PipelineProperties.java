package com.gole.api.order.application.service.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파이프라인 상태별 타임아웃. (shipping-and-fees R9.1 — 코드 상수 금지, 전부 외부화)
 *
 * <p>운영 중 정책 조정이 배포 없이 가능해야 한다. 기본값은 R9 표를 따른다.
 * record가 아닌 세터 바인딩 클래스인 이유: 이 빈은 AOP 프록시 대상이 될 수 있는데
 * record(final)는 CGLIB 서브클래싱이 불가능해 부팅이 실패한다({@code FeeProperties}와 동일 형식).
 */
@ConfigurationProperties(prefix = "gole.pipeline")
public class PipelineProperties {

    /** 결제 미승인 만료. (기본 30분) */
    private Duration paymentPendingExpiry = Duration.ofMinutes(30);

    /** 운송장 미등록 → 판매자 독촉. (기본 3일) */
    private Duration shipmentReminderAfter = Duration.ofDays(3);

    /** 운송장 미등록 → 자동 전액 환불. (기본 7일) */
    private Duration unshippedRefundAfter = Duration.ofDays(7);

    /** 송장 등록 후 택배사 미접수 → 예외 큐. (기본 3일) */
    private Duration carrierPickupTimeout = Duration.ofDays(3);

    /** 배송 정체 → 예외 큐. (기본 14일) */
    private Duration transitStallAfter = Duration.ofDays(14);

    /** 배송완료 + 무분쟁 → 자동 구매확정. (기본 7일) */
    private Duration autoCompleteAfter = Duration.ofDays(7);

    /** 분쟁 미판정 → 운영자 에스컬레이션. 자동 판정은 하지 않는다(R9.2). (기본 3일) */
    private Duration disputeEscalationAfter = Duration.ofDays(3);

    /** 트래커 연속 조회 실패 → 예외 큐. (기본 24시간) */
    private Duration trackerUnknownAfter = Duration.ofHours(24);

    public Duration paymentPendingExpiry() {
        return paymentPendingExpiry;
    }

    public Duration shipmentReminderAfter() {
        return shipmentReminderAfter;
    }

    public Duration unshippedRefundAfter() {
        return unshippedRefundAfter;
    }

    public Duration carrierPickupTimeout() {
        return carrierPickupTimeout;
    }

    public Duration transitStallAfter() {
        return transitStallAfter;
    }

    public Duration autoCompleteAfter() {
        return autoCompleteAfter;
    }

    public Duration disputeEscalationAfter() {
        return disputeEscalationAfter;
    }

    public Duration trackerUnknownAfter() {
        return trackerUnknownAfter;
    }

    public void setPaymentPendingExpiry(Duration paymentPendingExpiry) {
        this.paymentPendingExpiry = paymentPendingExpiry;
    }

    public void setShipmentReminderAfter(Duration shipmentReminderAfter) {
        this.shipmentReminderAfter = shipmentReminderAfter;
    }

    public void setUnshippedRefundAfter(Duration unshippedRefundAfter) {
        this.unshippedRefundAfter = unshippedRefundAfter;
    }

    public void setCarrierPickupTimeout(Duration carrierPickupTimeout) {
        this.carrierPickupTimeout = carrierPickupTimeout;
    }

    public void setTransitStallAfter(Duration transitStallAfter) {
        this.transitStallAfter = transitStallAfter;
    }

    public void setAutoCompleteAfter(Duration autoCompleteAfter) {
        this.autoCompleteAfter = autoCompleteAfter;
    }

    public void setDisputeEscalationAfter(Duration disputeEscalationAfter) {
        this.disputeEscalationAfter = disputeEscalationAfter;
    }

    public void setTrackerUnknownAfter(Duration trackerUnknownAfter) {
        this.trackerUnknownAfter = trackerUnknownAfter;
    }
}
