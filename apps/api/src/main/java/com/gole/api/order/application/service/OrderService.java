package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.CompleteOrderUseCase;
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
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import java.time.Clock;
import java.time.Instant;
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
                GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ListingReservationPort listingReservation;
    private final PaymentGatewayPort paymentGateway;
    private final SettlementPort settlement;
    private final ExecutedPriceRecorderPort executedPriceRecorder;
    private final OrderIdGeneratorPort idGenerator;
    private final Clock clock;

    public OrderService(
            OrderRepositoryPort orderRepository,
            ListingReservationPort listingReservation,
            PaymentGatewayPort paymentGateway,
            SettlementPort settlement,
            ExecutedPriceRecorderPort executedPriceRecorder,
            OrderIdGeneratorPort idGenerator,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.listingReservation = listingReservation;
        this.paymentGateway = paymentGateway;
        this.settlement = settlement;
        this.executedPriceRecorder = executedPriceRecorder;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public String place(PlaceOrderCommand command) {
        // 요구사항 13.1: 단일 문서 원자 갱신(findAndModify)이 단일 낙찰을 보장한다.
        // 트랜잭션 밖에서 수행해 동시 요청 시 패자는 write-conflict 대신 깔끔히 빈 결과를 받는다.
        ReservedListing reserved = listingReservation.reserve(command.listingId())
                .orElseThrow(() -> new ItemUnavailableException(command.listingId()));

        Order order = Order.place(
                idGenerator.newOrderId(),
                command.listingId(),
                command.buyerId(),
                reserved.sellerId(),
                reserved.catalogSetNumber(),
                reserved.price(),
                Instant.now(clock));
        return orderRepository.save(order).getId();
    }

    @Override
    @Transactional
    public OrderStatus pay(String orderId) {
        Order order = getById(orderId);
        Instant now = Instant.now(clock);

        boolean authorized = paymentGateway.authorize(orderId, order.getAmount());
        if (authorized) {
            order.confirmFundsHeld(now); // 요구사항 13.2
        } else {
            order.failPayment(now); // 요구사항 13.3: 자금 미보유
            listingReservation.release(order.getListingId());
        }
        orderRepository.save(order);
        return order.getStatus();
    }

    @Override
    @Transactional
    public void complete(String orderId) {
        Order order = getById(orderId);
        Instant now = Instant.now(clock);

        order.complete(now); // FUNDS_HELD → COMPLETED (불가 시 예외)
        listingReservation.markSold(order.getListingId());

        // 요구사항 9.1: 체결가 기록(카탈로그 연결 시)
        if (order.getCatalogSetNumber() != null) {
            executedPriceRecorder.record(order.getCatalogSetNumber(), order.getAmount(), 1, now);
        }
        // 요구사항 13.5: exactly-once 정산
        settlement.settleOnce(orderId, order.getSellerId(), order.getAmount());

        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void refund(String orderId) {
        Order order = getById(orderId);
        Instant now = Instant.now(clock);

        order.refund(now); // FUNDS_HELD → REFUNDED (불가 시 예외)
        paymentGateway.refund(orderId, order.getAmount());
        listingReservation.release(order.getListingId());
        orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
