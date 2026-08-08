package com.gole.api.order.application.service;

import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
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
import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult;
import com.gole.api.order.application.port.out.PaymentGatewayPort.RefundResult;
import com.gole.api.order.application.port.out.PaymentGatewayUnavailableException;
import com.gole.api.order.application.port.out.SellerNotifierPort;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문/결제/정산 유스케이스. 정합성 핵심.
 * 모든 변경은 트랜잭션 내에서 수행되며, 리스팅 조건부 선점 + 주문 낙관적 락으로
 * 단일 낙찰/자금 보존/멱등 정산을 보장한다. (설계 Property 1~4)
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
    private final OrderIdGeneratorPort idGenerator;
    private final Clock clock;

    public OrderService(
            OrderRepositoryPort orderRepository,
            ListingReservationPort listingReservation,
            PaymentGatewayPort paymentGateway,
            SettlementPort settlement,
            ExecutedPriceRecorderPort executedPriceRecorder,
            SellerNotifierPort sellerNotifier,
            OrderIdGeneratorPort idGenerator,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.listingReservation = listingReservation;
        this.paymentGateway = paymentGateway;
        this.settlement = settlement;
        this.executedPriceRecorder = executedPriceRecorder;
        this.sellerNotifier = sellerNotifier;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @OperationalSignal(
            category = Category.PAYMENT,
            title = "주문 생성",
            description = "결제 대기 주문이 생성되었습니다.",
            includeResult = true)
    public String place(PlaceOrderCommand command) {
        // 요구사항 13.1: 단일 문서 원자 갱신(findAndModify)이 단일 낙찰을 보장한다.
        // 트랜잭션 밖에서 수행해 동시 요청 시 패자는 write-conflict 대신 깔끔히 빈 결과를 받는다.
        ReservedListing reserved = listingReservation
                .reserve(command.listingId())
                .orElseThrow(() -> new ItemUnavailableException(command.listingId()));

        if (reserved.sellerId().equals(command.buyerId())) {
            listingReservation.release(command.listingId());
            throw new ForbiddenException("SELF_PURCHASE_NOT_ALLOWED", "자신의 매물은 구매할 수 없습니다");
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
                    Instant.now(clock));
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
    @Transactional
    @OperationalSignal(
            category = Category.PAYMENT,
            title = "결제 상태 변경",
            description = "결제 승인 검증이 끝났습니다.",
            includeArguments = 0,
            includeResult = true)
    public OrderStatus pay(String orderId) {
        Order order = getById(orderId);
        Instant now = Instant.now(clock);

        PaymentVerificationResult verification = paymentGateway.verifyPayment(orderId, order.getAmount());
        switch (verification) {
            case PAID -> order.confirmFundsHeld(now); // 요구사항 13.2
            case FAILED -> {
                order.failPayment(now); // 요구사항 13.3: PG가 최종 실패한 경우에만 선점 해제
                listingReservation.release(order.getListingId());
            }
            case PENDING, REVIEW_REQUIRED -> {
                // READY/PENDING은 아직 실패가 아니다. 금액 불일치·미지 상태도 운영 확인 전에는
                // 주문과 매물 선점을 보존해 이중 판매 및 결제 유실을 막는다.
                return order.getStatus();
            }
        }
        orderRepository.save(order);
        return order.getStatus();
    }

    @Override
    @Transactional
    @OperationalSignal(
            category = Category.PAYMENT,
            title = "거래 완료",
            description = "구매 확정과 정산 처리가 완료되었습니다.",
            includeArguments = 0)
    public void complete(String orderId) {
        Order order = getById(orderId);
        Instant now = Instant.now(clock);

        order.complete(now); // FUNDS_HELD → COMPLETED (불가 시 예외)
        listingReservation.markSold(order.getListingId());

        // 요구사항 9.1: 체결가 기록(카탈로그 연결 시)
        if (order.getCatalogSetNumber() != null) {
            executedPriceRecorder.record(
                    order.getCatalogSetNumber(), order.getAmount(), 1, now, order.getListingCondition());
        }
        // 요구사항 13.5: exactly-once 정산
        settlement.settleOnce(orderId, order.getSellerId(), order.getAmount());

        orderRepository.save(order);
    }

    @Override
    @Transactional
    @OperationalSignal(
            category = Category.PAYMENT,
            level = Level.WARNING,
            title = "결제 환불",
            description = "환불과 매물 선점 해제가 완료되었습니다.",
            includeArguments = 0)
    public void refund(String orderId) {
        Order order = getById(orderId);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return;
        }
        Instant now = Instant.now(clock);

        if (order.getStatus() == OrderStatus.REFUND_PENDING) {
            if (paymentGateway.isFullyRefunded(orderId, order.getAmount())) {
                finalizeRefund(order, now);
            }
            return;
        }

        RefundResult result = paymentGateway.refund(orderId, order.getAmount());
        if (result == RefundResult.REQUESTED) {
            order.requestRefund(now);
            orderRepository.save(order);
            return;
        }

        finalizeRefund(order, now);
    }

    @Override
    @Transactional
    public void confirmRefund(String orderId) {
        Order order = getById(orderId);
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return;
        }
        if (!paymentGateway.isFullyRefunded(orderId, order.getAmount())) {
            throw new PaymentGatewayUnavailableException(
                    orderId, new IllegalStateException("PG refund is not final yet"));
        }
        finalizeRefund(order, Instant.now(clock));
    }

    private void finalizeRefund(Order order, Instant now) {
        order.refund(now);
        listingReservation.release(order.getListingId());
        orderRepository.save(order);
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
}
