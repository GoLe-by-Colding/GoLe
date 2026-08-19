package com.gole.api.order.application.service;

import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.OpenDisputeUseCase;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.in.ResolveDisputeUseCase;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.DisputeReason;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 분쟁 유스케이스. (shipping-and-fees R4)
 *
 * <p>판정은 새 전이 경로를 만들지 않는다 — 환불은 {@link RefundOrderUseCase},
 * 완료는 {@link CompleteOrderUseCase}를 그대로 호출한다. 정산·시세기록·알림·매물복구가
 * 이미 그 안에 붙어 있어 별도 경로를 만들면 로직이 두 벌이 된다(설계 판단).
 */
@Service
public class DisputeService implements OpenDisputeUseCase, ResolveDisputeUseCase {

    private static final int DETAIL_MAX_LENGTH = 1_000;

    private final OrderRepositoryPort orders;
    private final RefundOrderUseCase refundOrder;
    private final CompleteOrderUseCase completeOrder;
    private final OrderEventNotifierPort notifier;
    private final OperationalEventPublisher operationalEvents;
    private final Clock clock;

    public DisputeService(
            OrderRepositoryPort orders,
            RefundOrderUseCase refundOrder,
            CompleteOrderUseCase completeOrder,
            OrderEventNotifierPort notifier,
            OperationalEventPublisher operationalEvents,
            Clock clock) {
        this.orders = orders;
        this.refundOrder = refundOrder;
        this.completeOrder = completeOrder;
        this.notifier = notifier;
        this.operationalEvents = operationalEvents;
        this.clock = clock;
    }

    @Override
    public void open(OpenDisputeCommand command) {
        Order order =
                orders.findById(command.orderId()).orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        if (!order.getBuyerId().equals(command.buyerId())) {
            throw new ForbiddenException("DISPUTE_ACCESS_DENIED", "주문의 구매자만 분쟁을 제기할 수 있습니다");
        }
        DisputeReason reason = DisputeReason.fromKey(command.reasonKey())
                .orElseThrow(() -> new BadRequestException("INVALID_DISPUTE_REASON", "분쟁 사유를 선택해 주세요"));
        String detail = command.detail() == null
                ? null
                : command.detail()
                        .trim()
                        .substring(0, Math.min(command.detail().trim().length(), DETAIL_MAX_LENGTH));
        Instant now = Instant.now(clock);

        order.openDispute(reason, detail, now); // FUNDS_HELD에서만 가능(불가 시 409)
        orders.save(order);

        notifier.disputeOpened(order.getSellerId(), order.getId(), reason.label());
        // 분쟁은 무개입 파이프라인의 유일한 사람 개입 지점 — 운영 채널에 즉시 알린다. (R7.6)
        operationalEvents.publish(new OperationalEvent(
                Category.APPLICATION,
                Level.WARNING,
                "거래 분쟁 접수",
                "구매자가 분쟁을 제기했습니다. 예외 큐에서 배송 사실과 함께 확인하세요.",
                Map.of("주문 ID", order.getId(), "사유", reason.label()),
                now));
    }

    @Override
    public void resolve(ResolveDisputeCommand command) {
        Order order =
                orders.findById(command.orderId()).orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw new BadRequestException("DISPUTE_NOT_OPEN", "분쟁 상태의 주문이 아닙니다: " + order.getStatus());
        }
        boolean refunded = command.resolution() == Resolution.REFUND;
        if (refunded) {
            refundOrder.refund(order.getId()); // 환불 경로 재사용 — 정산 미생성(R5.5)
        } else {
            completeOrder.complete(order.getId()); // 완료 경로 재사용 — 수수료 확정 + 정산
        }
        notifier.disputeResolved(order.getBuyerId(), order.getSellerId(), order.getId(), refunded);
    }
}
