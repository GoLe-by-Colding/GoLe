package com.gole.api.order.adapter.in.web;

import com.gole.api.order.adapter.in.web.OrderRequests.PlaceOrderRequest;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import com.gole.api.order.application.port.in.StartPaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 주문 라이프사이클. (요구사항 7, 13)
 */
@Tag(name = "Order", description = "주문 생성·결제·구매확정·환불·조회")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final StartPaymentUseCase startPaymentUseCase;
    private final PayOrderUseCase payOrderUseCase;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final RefundOrderUseCase refundOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    public OrderController(
            PlaceOrderUseCase placeOrderUseCase,
            StartPaymentUseCase startPaymentUseCase,
            PayOrderUseCase payOrderUseCase,
            CompleteOrderUseCase completeOrderUseCase,
            RefundOrderUseCase refundOrderUseCase,
            GetOrderUseCase getOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.startPaymentUseCase = startPaymentUseCase;
        this.payOrderUseCase = payOrderUseCase;
        this.completeOrderUseCase = completeOrderUseCase;
        this.refundOrderUseCase = refundOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        String id = placeOrderUseCase.place(new PlaceOrderCommand(request.listingId(), request.buyerId()));
        return OrderResponse.from(getOrderUseCase.getById(id));
    }

    @Operation(
            summary = "결제 시도 시작",
            description = "결제창을 열기 직전에 호출해 PG 결제 식별자를 받는다. 시도마다 새 값이 발급되므로 결제창을 닫았다가 다시 결제해도 막히지 않는다.")
    @PostMapping("/{orderId}/payment-attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentAttemptResponse startPayment(@PathVariable String orderId) {
        return new PaymentAttemptResponse(startPaymentUseCase.start(orderId));
    }

    /** 결제 시도 발급 결과. */
    public record PaymentAttemptResponse(String paymentId) {}

    @PostMapping("/{orderId}/payment")
    public OrderResponse pay(@PathVariable String orderId) {
        payOrderUseCase.pay(orderId);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @PostMapping("/{orderId}/completion")
    public OrderResponse complete(@PathVariable String orderId) {
        completeOrderUseCase.complete(orderId);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @PostMapping("/{orderId}/refund")
    public OrderResponse refund(@PathVariable String orderId) {
        refundOrderUseCase.refund(orderId);
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @Operation(summary = "주문 단건 조회")
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable String orderId) {
        return OrderResponse.from(getOrderUseCase.getById(orderId));
    }

    @Operation(summary = "내 구매 내역", description = "buyerId 기준 주문 목록(최신순). 프로필 내 주문 내역에 사용.")
    @GetMapping
    public List<OrderResponse> listByBuyer(@RequestParam String buyerId) {
        return getOrderUseCase.getByBuyerId(buyerId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(OrderResponse::from)
                .toList();
    }
}
