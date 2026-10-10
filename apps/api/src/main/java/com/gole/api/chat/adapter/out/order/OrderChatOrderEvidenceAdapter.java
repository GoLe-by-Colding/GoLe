package com.gole.api.chat.adapter.out.order;

import com.gole.api.chat.application.port.out.ChatOrderEvidencePort;
import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.domain.model.Order;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** 주문 컨텍스트 통합 어댑터. 종결 여부 판단은 주문 상태({@code OrderStatus.isTerminal})에 맡긴다. */
@Component
public class OrderChatOrderEvidenceAdapter implements ChatOrderEvidencePort {

    private final GetOrderUseCase orders;

    public OrderChatOrderEvidenceAdapter(GetOrderUseCase orders) {
        this.orders = orders;
    }

    @Override
    public boolean hasUnsettledOrder(String accountId) {
        return Stream.concat(orders.getByBuyerId(accountId).stream(), orders.getBySellerId(accountId).stream())
                .map(Order::getStatus)
                .anyMatch(status -> !status.isTerminal());
    }
}
