package com.gole.api.order.application.port.in;

/**
 * Inbound port: 구매 확정 → 완료(판매처리·체결가 기록·정산). (요구사항 7.4, 13.4, 13.5)
 */
public interface CompleteOrderUseCase {

    void complete(String orderId);

    /**
     * 스케줄러 전용 구매확정. 정확히 FUNDS_HELD인 주문만 완료하며, 그 사이 분쟁·환불로
     * 전이됐으면 아무것도 하지 않고 false를 반환한다.
     */
    default boolean completeAutomatically(String orderId) {
        complete(orderId);
        return true;
    }
}
