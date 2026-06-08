package com.gole.api.order.application.port.out;

/**
 * Outbound port: 정산. 완료 주문 1건당 정확히 1회(exactly-once) 정산을 보장한다.
 * (요구사항 13.4, 13.5)
 */
public interface SettlementPort {

    void settleOnce(String orderId, String sellerId, long amount);
}
