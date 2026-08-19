package com.gole.api.shipping.adapter.out.tracker;

import com.gole.api.shipping.application.port.out.DeliveryTrackerPort;
import com.gole.api.shipping.domain.model.DeliveryStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 배송 트래커 스텁 어댑터. (R6.2, R6.3)
 *
 * <p>{@code shipping.tracker.enabled=true} 이면 {@link DeliveryTrackerApiAdapter}가 대신
 * 사용된다(기본은 스텁 — {@code StubPaymentGatewayAdapter}와 같은 게이트 패턴).
 *
 * <p>등록 경과 시간으로 상태를 시뮬레이션한다: 등록 직후 {@code PENDING} →
 * 1분 후 {@code IN_TRANSIT} → 3분 후 {@code DELIVERED}. 로컬에서 전 구간
 * (등록 → 이동중 → 배송완료 → 자동 구매확정 → 정산)을 클릭만으로 확인할 수 있다.
 */
@Component
@ConditionalOnProperty(name = "shipping.tracker.enabled", havingValue = "false", matchIfMissing = true)
public class StubDeliveryTrackerAdapter implements DeliveryTrackerPort {

    private final Clock clock;
    private final Duration inTransitAfter;
    private final Duration deliveredAfter;

    public StubDeliveryTrackerAdapter(
            Clock clock,
            @Value("${shipping.tracker.stub-in-transit-after:PT1M}") Duration inTransitAfter,
            @Value("${shipping.tracker.stub-delivered-after:PT3M}") Duration deliveredAfter) {
        this.clock = clock;
        this.inTransitAfter = inTransitAfter;
        this.deliveredAfter = deliveredAfter;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public TrackingResult track(TrackingQuery query) {
        Duration elapsed = Duration.between(query.registeredAt(), Instant.now(clock));
        if (elapsed.compareTo(deliveredAfter) >= 0) {
            return new TrackingResult(DeliveryStatus.DELIVERED, "[STUB] 배달완료");
        }
        if (elapsed.compareTo(inTransitAfter) >= 0) {
            return new TrackingResult(DeliveryStatus.IN_TRANSIT, "[STUB] 간선상차");
        }
        return new TrackingResult(DeliveryStatus.PENDING, "[STUB] 접수대기");
    }
}
