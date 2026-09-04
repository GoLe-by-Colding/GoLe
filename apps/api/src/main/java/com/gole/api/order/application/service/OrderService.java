package com.gole.api.order.application.service;

import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.common.operations.OperationalSignal;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.ConfirmRefundUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.out.ExecutedPriceRecorderPort;
import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.ListingReservationPort.ReservedListing;
import com.gole.api.order.application.port.out.OrderEventNotifierPort;
import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerification;
import com.gole.api.order.application.port.out.PaymentGatewayPort.RefundResult;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import com.gole.api.order.application.port.out.SellerNotifierPort;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.application.service.OrderPaymentTransitionService.RefundPreparation;
import com.gole.api.order.application.service.OrderPaymentTransitionService.RefundStart;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.exception.SelfPurchaseException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.order.domain.model.PhoneNumber;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문/결제/정산 유스케이스. 정합성 핵심.
 * 외부 PG 호출은 트랜잭션 밖에서 수행하고, DB 상태 전이는 짧은 트랜잭션으로 반영한다.
 * 리스팅 조건부 선점 + 주문 낙관적 락으로 단일 낙찰/자금 보존/멱등 정산을 보장한다.
 * (설계 Property 1~4)
 */
@Service
public class OrderService
        implements PlaceOrderUseCase,
                PayOrderUseCase,
                CompleteOrderUseCase,
                RefundOrderUseCase,
                ConfirmRefundUseCase,
                GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ListingReservationPort listingReservation;
    private final PaymentGatewayPort paymentGateway;
    private final SettlementPort settlement;
    private final ExecutedPriceRecorderPort executedPriceRecorder;
    private final SellerNotifierPort sellerNotifier;
    private final OrderEventNotifierPort orderEventNotifier;
    private final OrderIdGeneratorPort idGenerator;
    private final Clock clock;
    private final OrderPaymentTransitionService paymentTransitions;
    private final OperationalEventPublisher operationalEvents;

    public OrderService(
            OrderRepositoryPort orderRepository,
            ListingReservationPort listingReservation,
            PaymentGatewayPort paymentGateway,
            SettlementPort settlement,
            ExecutedPriceRecorderPort executedPriceRecorder,
            SellerNotifierPort sellerNotifier,
            OrderEventNotifierPort orderEventNotifier,
            OrderIdGeneratorPort idGenerator,
            Clock clock,
            OrderPaymentTransitionService paymentTransitions,
            OperationalEventPublisher operationalEvents) {
        this.orderRepository = orderRepository;
        this.listingReservation = listingReservation;
        this.paymentGateway = paymentGateway;
        this.settlement = settlement;
        this.executedPriceRecorder = executedPriceRecorder;
        this.sellerNotifier = sellerNotifier;
        this.orderEventNotifier = orderEventNotifier;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.paymentTransitions = paymentTransitions;
        this.operationalEvents = operationalEvents;
    }

    @Override
    public String place(PlaceOrderCommand command) {
        // 요구사항 13.1: 단일 문서 원자 갱신(findAndModify)이 단일 낙찰을 보장한다.
        // 트랜잭션 밖에서 수행해 동시 요청 시 패자는 write-conflict 대신 깔끔히 빈 결과를 받는다.
        ReservedListing reserved = listingReservation
                .reserve(command.listingId())
                .orElseThrow(() -> new ItemUnavailableException(command.listingId()));

        // 자기거래 금지: 완료된 주문은 체결가로 기록되어 시세의 원천이 되므로,
        // 자전거래를 허용하면 시세를 임의로 만들 수 있다.
        // 판매자는 선점 결과로만 알 수 있어 선점 이후에 검사하고, 선점을 되돌린 뒤 거부한다.
        if (reserved.sellerId().equals(command.buyerId())) {
            listingReservation.release(command.listingId());
            throw new SelfPurchaseException(command.listingId());
        }

        String orderId;
        try {
            Order order = Order.place(
                    idGenerator.newOrderId(),
                    command.listingId(),
                    command.buyerId(),
                    reserved.sellerId(),
                    reserved.catalogSetNumber(),
                    reserved.condition(),
                    reserved.price(),
                    PhoneNumber.ofNullable(command.buyerPhone()),
                    Instant.now(clock));
            // 브라우저가 금액을 바꿔 요청하더라도 결제되기 전에 PortOne 원장과 주문 금액을 고정한다.
            // 외부 I/O 또는 저장 실패 시 아래 보상 경로에서 매물 선점을 해제한다.
            paymentGateway.preparePayment(order.getId(), order.getAmount());
            orderId = orderRepository.save(order).getId();
        } catch (RuntimeException failure) {
            // 선점은 Mongo 원자 갱신으로 트랜잭션 밖에서 수행하므로 주문 저장 전 실패는 직접 보상한다.
            try {
                listingReservation.release(command.listingId());
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }

        // 알림 N6: 셀러에게 주문 알림(best-effort, 어댑터가 예외 흡수)
        sellerNotifier.notifyOrderPlaced(reserved.sellerId(), orderId, reserved.price());
        return orderId;
    }

    @Override
    public OrderStatus pay(String orderId) {
        Order order = getById(orderId);
        OrderStatus previousStatus = order.getStatus();
        PaymentVerification verification = paymentGateway.verifyPayment(orderId, order.getAmount());
        OrderStatus status = paymentTransitions.applyPaymentVerification(orderId, verification, Instant.now(clock));
        publishPaymentDecision(orderId, previousStatus, status);
        return status;
    }

    @Override
    @OperationalSignal(
            category = Category.PAYMENT,
            title = "거래 완료",
            description = "구매 확정과 정산 원장 적재가 완료되었습니다.",
            includeArguments = 0)
    @Transactional
    public void complete(String orderId) {
        Order order = getById(orderId);
        completeAndRecord(order, Instant.now(clock));
        orderEventNotifier.completed(order.getBuyerId(), order.getSellerId(), orderId);
    }

    @Override
    @Transactional
    public boolean completeAutomatically(String orderId) {
        Order order = getById(orderId);
        if (order.getStatus() != OrderStatus.FUNDS_HELD) {
            return false;
        }
        completeAndRecord(order, Instant.now(clock));
        return true;
    }

    private void completeAndRecord(Order order, Instant now) {

        order.complete(now); // FUNDS_HELD → COMPLETED (불가 시 예외)
        listingReservation.markSold(order.getListingId());

        // 요구사항 9.1: 체결가 기록(카탈로그 연결 시)
        if (order.getCatalogSetNumber() != null) {
            executedPriceRecorder.record(
                    order.getId(),
                    order.getCatalogSetNumber(),
                    order.getAmount(),
                    1,
                    now,
                    order.getListingCondition(),
                    order.getPaymentEvidenceKind());
        }
        // 요구사항 13.5: exactly-once 정산
        settlement.settleOnce(order.getId(), order.getSellerId(), order.getAmount());

        orderRepository.save(order);
    }

    @Override
    public void refund(String orderId) {
        // 결제 OFF 운영의 Stub은 상태를 바꾸기 전에 거부한다. 이 확인이 beginRefund 뒤로
        // 내려가면 실제 환불 호출 없이 주문만 REFUND_PENDING에 영구 고정될 수 있다.
        paymentGateway.requireAvailable(orderId);
        Instant now = Instant.now(clock);
        RefundPreparation preparation = paymentTransitions.beginRefund(orderId, now);
        Order order = preparation.order();
        if (preparation.result() == RefundStart.ALREADY_REFUNDED) {
            return;
        }

        if (preparation.result() == RefundStart.ALREADY_PENDING) {
            if (paymentGateway.isFullyRefunded(orderId, order.getAmount())) {
                Instant confirmedAt = Instant.now(clock);
                paymentTransitions.finalizeRefund(orderId, confirmedAt);
                publishRefundCompleted(orderId, confirmedAt);
                return;
            }
            // 이전 취소 호출이 PG에 도달하기 전에 끊겼을 수 있다. 어댑터는 먼저 원장의
            // 기존 REQUESTED/CANCELLED 취소를 확인하고 currentCancellableAmount로 잔액을
            // 고정하므로 같은 주문의 재조정 호출이 중복 환불을 만들지 않는다.
        }

        RefundResult result = paymentGateway.refund(orderId, order.getAmount());
        if (result == RefundResult.REQUESTED) {
            if (preparation.result() == RefundStart.STARTED) {
                publishPaymentEvent(
                        Level.WARNING,
                        "환불 처리 대기",
                        "PG에 환불이 접수되어 최종 완료를 기다리고 있습니다.",
                        orderId,
                        OrderStatus.REFUND_PENDING,
                        now);
            }
            return;
        }

        paymentTransitions.finalizeRefund(orderId, now);
        publishRefundCompleted(orderId, now);
    }

    @Override
    public void confirmRefund(String orderId) {
        paymentGateway.requireAvailable(orderId);
        Instant now = Instant.now(clock);
        RefundPreparation preparation = paymentTransitions.beginRefund(orderId, now);
        Order order = preparation.order();
        if (preparation.result() == RefundStart.ALREADY_REFUNDED) {
            return;
        }
        if (!paymentGateway.isFullyRefunded(orderId, order.getAmount())) {
            throw new PaymentGatewayUnavailableException(
                    orderId, new IllegalStateException("PG refund is not final yet"));
        }
        Instant confirmedAt = Instant.now(clock);
        paymentTransitions.finalizeRefund(orderId, confirmedAt);
        publishRefundCompleted(orderId, confirmedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getById(String orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getByBuyerId(String buyerId) {
        return orderRepository.findByBuyerId(buyerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getBySellerId(String sellerId) {
        return orderRepository.findBySellerId(sellerId);
    }

    private void publishPaymentDecision(String orderId, OrderStatus previousStatus, OrderStatus status) {
        if (status == previousStatus) {
            return;
        }
        Instant now = Instant.now(clock);
        switch (status) {
            case FUNDS_HELD -> publishPaymentEvent(
                    Level.SUCCESS, "결제 승인 완료", "PG 원장 검증 후 결제 금액이 안전하게 보유되었습니다.", orderId, status, now);
            case PAYMENT_REVIEW -> publishPaymentEvent(
                    Level.WARNING, "결제 수동 확인 대기", "결제를 자동 확정하지 않고 관리자 검토 상태로 보존했습니다.", orderId, status, now);
            case PAYMENT_FAILED -> publishPaymentEvent(
                    Level.ERROR, "결제 실패 확정", "PG 원장에서 결제 실패 상태가 확인되어 매물 선점을 해제했습니다.", orderId, status, now);
            default -> {
                // 결제 대기나 이미 처리된 상태는 운영 채널의 불필요한 사용자 취소 알림을 만들지 않는다.
            }
        }
    }

    private void publishRefundCompleted(String orderId, Instant occurredAt) {
        publishPaymentEvent(
                Level.SUCCESS,
                "환불 완료",
                "PG 원장에서 전액 환불을 확인하고 매물 선점을 해제했습니다.",
                orderId,
                OrderStatus.REFUNDED,
                occurredAt);
    }

    private void publishPaymentEvent(
            Level level, String title, String description, String orderId, OrderStatus status, Instant occurredAt) {
        operationalEvents.publish(new OperationalEvent(
                Category.PAYMENT,
                level,
                title,
                description,
                Map.of("주문 ID", orderId, "상태", status.name()),
                occurredAt));
    }
}
