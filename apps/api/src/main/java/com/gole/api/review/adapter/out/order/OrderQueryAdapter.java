package com.gole.api.review.adapter.out.order;

import com.gole.api.order.application.port.in.GetOrderUseCase;
import com.gole.api.order.domain.exception.OrderNotFoundException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.order.domain.model.OrderStatus;
import com.gole.api.review.application.port.out.OrderQueryPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * CROSS-CONTEXT 어댑터: 후기의 {@link OrderQueryPort} 출력 포트를
 * 주문 컨텍스트의 인바운드 유스케이스 {@link GetOrderUseCase}로 연결한다.
 *
 * <p>깨끗한 컨텍스트 경계: 후기의 아웃바운드 어댑터가 주문의 인바운드 포트에만 의존하며,
 * 주문의 내부 영속성에는 직접 접근하지 않는다. 주문 도메인 {@link Order}를 경량 스냅샷으로 변환한다.
 */
@Component
public class OrderQueryAdapter implements OrderQueryPort {

    private final GetOrderUseCase getOrder;

    public OrderQueryAdapter(GetOrderUseCase getOrder) {
        this.getOrder = getOrder;
    }

    @Override
    public Optional<OrderSnapshot> findById(String orderId) {
        try {
            Order order = getOrder.getById(orderId);
            return Optional.of(new OrderSnapshot(
                    order.getId(),
                    order.getBuyerId(),
                    order.getSellerId(),
                    order.getStatus() == OrderStatus.COMPLETED));
        } catch (OrderNotFoundException ex) {
            return Optional.empty();
        }
    }
}
