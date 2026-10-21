package com.gole.api.order.adapter.in.web;

import com.gole.api.common.web.auth.AuthenticatedUser;
import com.gole.api.order.application.port.in.CancelOrderUseCase;
import com.gole.api.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기존 주문의 취소는 신규 결제 공개 여부와 별개로 구매자에게 허용한다. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderCancellationController {
    private final CancelOrderUseCase cancelOrder;
    private final GetOrderUseCase orders;

    public OrderCancellationController(CancelOrderUseCase cancelOrder, GetOrderUseCase orders) {
        this.cancelOrder = cancelOrder;
        this.orders = orders;
    }

    @PostMapping("/{orderId}/cancellation")
    public OrderResponse cancel(@PathVariable String orderId, HttpServletRequest request) {
        cancelOrder.cancel(new CancelOrderCommand(orderId, AuthenticatedUser.id(request)));
        return OrderResponse.from(orders.getById(orderId));
    }
}
