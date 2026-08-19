package com.gole.api.shipping.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.shipping.domain.exception.ShipmentStateException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShipmentTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private static Shipment shipment() {
        return Shipment.register(
                "s-1",
                "order-1",
                "seller-1",
                "buyer-1",
                "01012345678",
                Carrier.CJ_LOGISTICS,
                new WaybillNumber("123456789012"),
                T0);
    }

    @Test
    void tracking_advancesForwardOnly() {
        Shipment s = shipment();
        assertThat(s.applyTracking(DeliveryStatus.IN_TRANSIT, "간선상차", T0.plusSeconds(60)))
                .isFalse();
        assertThat(s.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);

        // 역행 무시 — 외부 API가 흔들려도 되돌리지 않는다
        s.applyTracking(DeliveryStatus.PENDING, "접수", T0.plusSeconds(120));
        assertThat(s.getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void deliveredAt_isRecordedOnceAndReturnsTrueOnlyOnFirstTransition() {
        Shipment s = shipment();
        Instant deliveredAt = T0.plusSeconds(300);
        assertThat(s.applyTracking(DeliveryStatus.DELIVERED, "배달완료", deliveredAt))
                .isTrue();
        assertThat(s.getDeliveredAt()).isEqualTo(deliveredAt);

        // 같은 결과를 다시 반영해도 시각이 바뀌거나 재알림되지 않는다
        assertThat(s.applyTracking(DeliveryStatus.DELIVERED, "배달완료", deliveredAt.plusSeconds(600)))
                .isFalse();
        assertThat(s.getDeliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    void unknown_tracksDurationWithoutChangingStatus() {
        Shipment s = shipment();
        Instant firstUnknown = T0.plusSeconds(60);
        s.applyTracking(DeliveryStatus.UNKNOWN, null, firstUnknown);
        assertThat(s.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(s.getUnknownSince()).isEqualTo(firstUnknown);

        // 연속 UNKNOWN이면 최초 시각 유지(24시간 판정 기준), 정상 조회되면 해제
        s.applyTracking(DeliveryStatus.UNKNOWN, null, firstUnknown.plusSeconds(600));
        assertThat(s.getUnknownSince()).isEqualTo(firstUnknown);
        s.applyTracking(DeliveryStatus.IN_TRANSIT, "이동중", firstUnknown.plusSeconds(1200));
        assertThat(s.getUnknownSince()).isNull();
    }

    @Test
    void replaceWaybill_keepsHistoryAndResetsTracking() {
        Shipment s = shipment();
        s.applyTracking(DeliveryStatus.IN_TRANSIT, "이동중", T0.plusSeconds(60));
        s.replaceWaybill(Carrier.HANJIN, new WaybillNumber("99887766554"), null, T0.plusSeconds(120));

        assertThat(s.getCarrier()).isEqualTo(Carrier.HANJIN);
        assertThat(s.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(s.getSellerPhone()).isEqualTo("01012345678"); // null이면 기존 연락처 유지
        assertThat(s.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.carrier()).isEqualTo(Carrier.CJ_LOGISTICS);
            assertThat(h.waybillNumber()).isEqualTo("123456789012");
        });
    }

    @Test
    void replaceWaybill_isForbiddenAfterDelivery() {
        Shipment s = shipment();
        s.applyTracking(DeliveryStatus.DELIVERED, "배달완료", T0.plusSeconds(60));
        assertThatThrownBy(() ->
                        s.replaceWaybill(Carrier.HANJIN, new WaybillNumber("99887766554"), null, T0.plusSeconds(120)))
                .isInstanceOf(ShipmentStateException.class);
    }
}
