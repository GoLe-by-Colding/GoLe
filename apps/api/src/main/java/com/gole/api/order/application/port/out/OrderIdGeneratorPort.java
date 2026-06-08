package com.gole.api.order.application.port.out;

/**
 * Outbound port: 주문 식별자 생성.
 */
public interface OrderIdGeneratorPort {

    String newOrderId();
}
