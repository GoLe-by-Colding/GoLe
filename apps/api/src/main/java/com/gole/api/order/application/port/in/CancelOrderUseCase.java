package com.gole.api.order.application.port.in;

/** 구매자의 미결제 주문 취소. PG 원장 확인 뒤 예약을 복원한다. */
public interface CancelOrderUseCase {
    void cancel(CancelOrderCommand command);

    record CancelOrderCommand(String orderId, String buyerId) {}
}
