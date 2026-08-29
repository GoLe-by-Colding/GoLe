package com.gole.api.order.application.service.pipeline;

import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.shipping.application.port.in.GetShipmentUseCase;
import com.gole.api.shipping.domain.model.Shipment;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 자동 구매확정. (R3.2, R9: DELIVERED + 무분쟁 7일 → COMPLETED + 수수료 확정 + 정산)
 *
 * <p>기존 {@link CompleteOrderUseCase}를 그대로 호출한다 — 정산·시세기록·매물 판매확정이
 * 이미 그 안에 붙어 있다(설계 판단: 새 전이 경로 금지). 분쟁이 열리면 주문이
 * {@code DISPUTED}가 되어 아래 상태 검사에서 자연히 제외된다 — 타이머 정지(R4.2).
 */
@Component
public class AutoCompleteDeliveredRule implements PipelineRule {

    private final GetShipmentUseCase shipments;
    private final OrderRepositoryPort orders;
    private final CompleteOrderUseCase completeOrder;
    private final OrderEventNotifierPort notifier;
    private final PipelineProperties properties;

    public AutoCompleteDeliveredRule(
            GetShipmentUseCase shipments,
            OrderRepositoryPort orders,
            CompleteOrderUseCase completeOrder,
            OrderEventNotifierPort notifier,
            PipelineProperties properties) {
        this.shipments = shipments;
        this.orders = orders;
        this.completeOrder = completeOrder;
        this.notifier = notifier;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "auto-complete-delivered";
    }

    @Override
    public List<String> candidates(Instant now) {
        return shipments.findDeliveredBefore(now.minus(properties.autoCompleteAfter())).stream()
                .map(Shipment::getOrderId)
                .toList();
    }

    @Override
    public boolean apply(String orderId, Instant now) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.FUNDS_HELD) {
            // 이미 완료·환불·분쟁 — 멱등(R3.3). 분쟁 중이면 확정하지 않는다(R4.2).
            return false;
        }
        try {
            if (!completeOrder.completeAutomatically(orderId)) {
                return false;
            }
        } catch (OptimisticLockingFailureException racedWithUserAction) {
            // FUNDS_HELD를 읽은 직후 사용자가 분쟁·환불을 열 수 있다. 낙관적 락 패배는
            // 실패가 아니라 "사용자 전이를 우선함"으로 처리하고 알림도 보내지 않는다.
            return false;
        }
        notifier.autoCompleted(order.getBuyerId(), order.getSellerId(), orderId);
        return true;
    }
}
