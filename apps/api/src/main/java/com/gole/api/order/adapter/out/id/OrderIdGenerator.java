package com.gole.api.order.adapter.out.id;

import com.gole.api.order.application.port.out.OrderIdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUID 기반 주문 식별자 생성 어댑터.
 */
@Component
public class OrderIdGenerator implements OrderIdGeneratorPort {

    @Override
    public String newOrderId() {
        return UUID.randomUUID().toString();
    }
}
