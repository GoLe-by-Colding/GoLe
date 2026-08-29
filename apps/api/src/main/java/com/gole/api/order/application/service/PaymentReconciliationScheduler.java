package com.gole.api.order.application.service;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 웹훅 유실·브라우저 종료로 남은 결제·환불 대기 주문을 PortOne 원장과 주기적으로 맞춘다. */
@Component
@ConditionalOnProperty(name = "portone.enabled", havingValue = "true")
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final OrderRepositoryPort orders;
    private final PaymentGatewayPort paymentGateway;
    private final OrderPaymentTransitionService transitions;
    private final RefundOrderUseCase refunds;
    private final OperationalEventPublisher operationalEvents;
    private final Clock clock;
    private final Duration minimumAge;
    private String refundCursor;

    public PaymentReconciliationScheduler(
            OrderRepositoryPort orders,
            PaymentGatewayPort paymentGateway,
            OrderPaymentTransitionService transitions,
            RefundOrderUseCase refunds,
            OperationalEventPublisher operationalEvents,
            Clock clock,
            @Value("${portone.reconciliation.minimum-age:PT10M}") Duration minimumAge) {
        this.orders = orders;
        this.paymentGateway = paymentGateway;
        this.transitions = transitions;
        this.refunds = refunds;
        this.operationalEvents = operationalEvents;
        this.clock = clock;
        this.minimumAge = minimumAge;
    }

    @Scheduled(
            initialDelayString = "${portone.reconciliation.initial-delay:PT1M}",
            fixedDelayString = "${portone.reconciliation.interval:PT5M}")
    public void reconcileStalePayments() {
        Instant now = Instant.now(clock);
        var candidates = orders.findPaymentPendingCreatedBefore(now.minus(minimumAge));
        int transitioned = 0;
        int expired = 0;
        int failed = 0;
        for (Order order : candidates) {
            try {
                PaymentVerification verification = paymentGateway.verifyPayment(order.getId(), order.getAmount());
                OrderStatus status;
                if (verification.result() == PaymentVerificationResult.NOT_FOUND) {
                    status = transitions.expireMissingPayment(order.getId(), now);
                    if (status == OrderStatus.PAYMENT_FAILED) {
                        expired++;
                    }
                } else {
                    status = transitions.applyPaymentVerification(order.getId(), verification, now);
                }
                if (status != OrderStatus.PAYMENT_PENDING) {
                    transitioned++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn(
                        "[payment reconciliation] orderId={} failed={}",
                        order.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        int refundChecked = 0;
        int refundCompleted = 0;
        var pendingRefunds = nextRefundBatch().stream()
                .filter(order -> !order.getStatusChangedAt().isAfter(now.minus(minimumAge)))
                .toList();
        for (Order order : pendingRefunds) {
            try {
                refunds.refund(order.getId());
                refundChecked++;
                if (orders.findById(order.getId())
                        .map(latest -> latest.getStatus() == OrderStatus.REFUNDED)
                        .orElse(false)) {
                    refundCompleted++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn(
                        "[refund reconciliation] orderId={} failed={}",
                        order.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        if (transitioned > 0 || refundChecked > 0 || failed > 0) {
            operationalEvents.publish(new OperationalEvent(
                    Category.PAYMENT,
                    failed > 0 ? Level.WARNING : Level.INFO,
                    "결제·환불 대기 자동 재조정",
                    "오래된 결제 및 환불 대기 주문을 PortOne 원장과 대조했습니다.",
                    Map.of(
                            "검사", Integer.toString(candidates.size()),
                            "상태 변경", Integer.toString(transitioned),
                            "결제 미시작 만료", Integer.toString(expired),
                            "환불 재조정", Integer.toString(refundChecked),
                            "환불 완료", Integer.toString(refundCompleted),
                            "재시도 필요", Integer.toString(failed)),
                    now));
        }
    }

    /**
     * REFUND_PENDING 전체를 여러 실행에 걸쳐 순환한다. 한 페이지가 계속 실패하더라도 커서는
     * 진행하며, 끝에 도달하면 첫 페이지로 돌아와 이전 실패 건도 다시 시도한다.
     */
    private List<Order> nextRefundBatch() {
        var batch = orders.findByStatusAfterId(OrderStatus.REFUND_PENDING, refundCursor);
        if (batch.isEmpty() && refundCursor != null) {
            refundCursor = null;
            batch = orders.findByStatusAfterId(OrderStatus.REFUND_PENDING, null);
        }
        if (!batch.isEmpty()) {
            refundCursor = batch.getLast().getId();
        }
        return batch;
    }
}
