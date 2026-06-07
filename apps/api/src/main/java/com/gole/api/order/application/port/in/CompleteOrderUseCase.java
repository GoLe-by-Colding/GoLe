package com.gole.api.order.application.port.in;

/**
 * Inbound port: 구매 확정 → 완료(판매처리·체결가 기록·정산). (요구사항 7.4, 13.4, 13.5)
 */
public interface CompleteOrderUseCase {

    void complete(String orderId);
}
