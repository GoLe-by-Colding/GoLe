package com.gole.api.order.application.port.out;

/**
 * Outbound port: 결제 게이트웨이(PG) 연동. 자금 보유(authorize) 및 환불.
 * (요구사항 13.2, 13.3, 13.6)
 */
public interface PaymentGatewayPort {

    enum RefundResult {
        SUCCEEDED,
        REQUESTED
    }

    /** 결제 승인(자금 보유). 성공 시 true. */
    boolean authorize(String orderId, long amount);

    /** 환불 처리. PG가 비동기 접수만 한 경우 REQUESTED를 반환한다. */
    RefundResult refund(String orderId, long amount);

    /** PG 원장 재조회로 전액 환불 완료 여부를 확인한다. */
    boolean isFullyRefunded(String orderId, long amount);
}
