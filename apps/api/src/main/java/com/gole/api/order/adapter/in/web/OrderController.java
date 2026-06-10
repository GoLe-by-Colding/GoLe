package com.gole.api.order.adapter.in.web;

import com.gole.api.order.adapter.in.web.OrderRequests.PlaceOrderRequest;
import com.gole.api.order.application.port.in.CompleteOrderUseCase;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase;
import com.gole.api.order.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.gole.api.order.application.port.in.RefundOrderUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final PayOrderUseCase payOrderUseCase;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final RefundOrderUseCase refundOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    public OrderController(
            PlaceOrderUseCase placeOrderUseCase,
            PayOrderUseCase payOrderUseCase,
            CompleteOrderUseCase completeOrderUseCase,
            RefundOrderUseCase refundOrderUseCase,
            GetOrderUseCase getOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
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
    public java.util.List<OrderResponse> listByBuyer(@RequestParam String buyerId) {
        return getOrderUseCase.getByBuyerId(buyerId).stream()
                .sorted(java.util.Comparator.comparing(
                        o -> o.getCreatedAt(), java.util.Comparator.reverseOrder()))
                .map(OrderResponse::from)
                .toList();
    }
}
