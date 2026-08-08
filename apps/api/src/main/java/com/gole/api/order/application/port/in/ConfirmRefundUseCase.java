package com.gole.api.order.application.port.in;

/** PG 웹훅을 서버 재조회로 검증한 뒤 환불 완료를 확정한다. */
public interface ConfirmRefundUseCase {

    void confirmRefund(String orderId);
}
