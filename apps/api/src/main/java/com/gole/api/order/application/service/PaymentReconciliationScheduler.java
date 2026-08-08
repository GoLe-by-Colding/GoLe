package com.gole.api.order.application.service;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 웹훅 유실·브라우저 종료로 남은 오래된 결제 대기 주문을 PortOne 원장과 주기적으로 맞춘다. */
@Component
@ConditionalOnProperty(name = "portone.enabled", havingValue = "true")
public class PaymentReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final OrderRepositoryPort orders;
    private final PaymentGatewayPort paymentGateway;
    private final OrderPaymentTransitionService transitions;
    private final OperationalEventPublisher operationalEvents;
    private final Clock clock;
    private final Duration minimumAge;

    public PaymentReconciliationScheduler(
            OrderRepositoryPort orders,
            PaymentGatewayPort paymentGateway,
            OrderPaymentTransitionService transitions,
            OperationalEventPublisher operationalEvents,
            Clock clock,
            @Value("${portone.reconciliation.minimum-age:PT10M}") Duration minimumAge) {
        this.orders = orders;
        this.paymentGateway = paymentGateway;
        this.transitions = transitions;
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
        int failed = 0;
        for (Order order : candidates) {
            try {
                PaymentVerificationResult result = paymentGateway.verifyPayment(order.getId(), order.getAmount());
                OrderStatus status = transitions.applyPaymentVerification(order.getId(), result, now);
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
        if (transitioned > 0 || failed > 0) {
            operationalEvents.publish(new OperationalEvent(
                    Category.PAYMENT,
                    failed > 0 ? Level.WARNING : Level.INFO,
                    "결제 대기 자동 재조정",
                    "오래된 결제 대기 주문을 PortOne 원장과 대조했습니다.",
                    Map.of(
                            "검사", Integer.toString(candidates.size()),
                            "상태 변경", Integer.toString(transitioned),
                            "재시도 필요", Integer.toString(failed)),
                    now));
        }
    }
}
