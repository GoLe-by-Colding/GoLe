package com.gole.api.order.application.service;

import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalSignal;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.in.StartPaymentUseCase;
import com.gole.api.order.application.port.out.ExecutedPriceRecorderPort;
import com.gole.api.order.application.port.out.ListingReservationPort;
import com.gole.api.order.application.port.out.ListingReservationPort.ReservedListing;
import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import com.gole.api.order.application.port.out.OrderRepositoryPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentAuthorization;
import com.gole.api.order.application.port.out.SellerNotifierPort;
import com.gole.api.order.application.port.out.SettlementPort;
import com.gole.api.order.domain.exception.ItemUnavailableException;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.exception.SelfPurchaseException;
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
                StartPaymentUseCase,
                PayOrderUseCase,
                CompleteOrderUseCase,
                RefundOrderUseCase,
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

        // 자기거래 금지: 완료된 주문은 체결가로 기록되어 시세의 원천이 되므로,
        // 자전거래를 허용하면 시세를 임의로 만들 수 있다.
        // 판매자는 선점 결과로만 알 수 있어 선점 이후에 검사하고, 선점을 되돌린 뒤 거부한다.
        if (reserved.sellerId().equals(command.buyerId())) {
            listingReservation.release(command.listingId());
            throw new SelfPurchaseException(command.listingId());
        }

        Order order = Order.place(
                idGenerator.newOrderId(),
                command.listingId(),
                command.buyerId(),
                reserved.sellerId(),
                reserved.catalogSetNumber(),
                reserved.condition(),
                reserved.price(),
                Instant.now(clock));
        String orderId = orderRepository.save(order).getId();

        // 알림 N6: 셀러에게 주문 알림(best-effort, 어댑터가 예외 흡수)
        sellerNotifier.notifyOrderPlaced(reserved.sellerId(), orderId, reserved.price());
        return orderId;
    }

    /**
     * 새 결제 시도를 시작한다. 브라우저는 여기서 받은 식별자로 결제창을 연다.
     *
     * <p>시도 횟수를 늘린 상태를 즉시 저장한다. 결제창이 열리기 전에 서버가 그 식별자를 알고
     * 있어야 뒤이은 검증·환불·웹훅이 같은 것을 가리킨다.
     */
    @Override
    @Transactional
    public String start(String orderId) {
        Order order = getById(orderId);
        String paymentId = order.beginPaymentAttempt(Instant.now(clock));
        orderRepository.save(order);
        return paymentId;
    }

    /**
     * 웹훅 진입점. 결제 식별자로 주문을 되찾은 뒤 같은 검증 경로를 탄다.
     *
     * <p>결제 식별자는 시도마다 달라지므로 주문 id로 바로 조회할 수 없다.
     */
    @Override
    @Transactional
    public OrderStatus payByPaymentId(String paymentId) {
        Order order =
                orderRepository.findByPaymentId(paymentId).orElseThrow(() -> new OrderNotFoundException(paymentId));
        return pay(order.getId());
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

        // 검증 대상은 주문 id가 아니라 이번 시도의 결제 식별자다. 재시도한 주문에서 둘은 다르다.
        PaymentAuthorization authorization = paymentGateway.authorize(order.getPaymentId(), order.getAmount());
        if (authorization.approved()) {
            // 결제수단은 승인 응답에만 실려 온다. 여기서 주문에 새기지 않으면 되찾을 곳이 없다.
            order.confirmFundsHeld(now, authorization.method()); // 요구사항 13.2
        } else {
            order.failPayment(now); // 요구사항 13.3: 자금 미보유
            listingReservation.release(order.getListingId());
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
        // 요구사항 13.5: exactly-once 정산.
        // 전표를 주문에 붙여 상태 전이와 같은 저장으로 커밋한다. 정산을 별도 쓰기로 두면
        // 뒤따르는 save(order)가 그것을 덮어써 수수료·정산액이 유실된다.
        order.attachSettlement(settlement.settleOnce(orderId, order.getSellerId(), order.getAmount()));

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
        Instant now = Instant.now(clock);

        order.refund(now); // FUNDS_HELD → REFUNDED (불가 시 예외)
        // 환불도 결제 식별자 기준이다. 주문 id로 요청하면 재시도된 주문의 환불이 대상을 못 찾는다.
        paymentGateway.refund(order.getPaymentId(), order.getAmount());
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
