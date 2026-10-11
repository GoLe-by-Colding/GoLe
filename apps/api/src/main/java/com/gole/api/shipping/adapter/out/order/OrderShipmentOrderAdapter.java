package com.gole.api.shipping.adapter.out.order;

import com.gole.api.order.application.port.in.PrepareShipmentRegistrationUseCase;
import com.gole.api.order.domain.exception.OrderStateException;
import com.gole.api.order.domain.model.Order;
import com.gole.api.shipping.application.port.out.ShipmentOrderPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 주문 컨텍스트 통합 어댑터. 배송 등록 펜스를 order 의 인바운드 포트로 선점하고 주문 당사자만 꺼낸다. */
@Component
public class OrderShipmentOrderAdapter implements ShipmentOrderPort {

    private final PrepareShipmentRegistrationUseCase prepareShipment;

    public OrderShipmentOrderAdapter(PrepareShipmentRegistrationUseCase prepareShipment) {
        this.prepareShipment = prepareShipment;
    }

    @Override
    public Optional<ShippableOrder> fenceForRegistration(String orderId, String sellerId) {
        Order order;
        try {
            order = prepareShipment.prepare(orderId, sellerId);
        } catch (OrderStateException invalidOrderState) {
            // 낙관적 락 충돌은 이 예외가 아니므로 그대로 전파돼 전역 CONCURRENT_UPDATE_CONFLICT 응답이 된다.
            return Optional.empty();
        }
        return Optional.of(new ShippableOrder(order.getId(), order.getSellerId(), order.getBuyerId()));
    }
}
