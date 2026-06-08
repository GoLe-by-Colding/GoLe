package com.gole.api.order.application.port.out;

/**
 * Outbound port: 결제 게이트웨이(PG) 연동. 자금 보유(authorize) 및 환불.
 * (요구사항 13.2, 13.3, 13.6)
 */
public interface PaymentGatewayPort {

    /** 결제 승인(자금 보유). 성공 시 true. */
    boolean authorize(String orderId, long amount);

    /** 환불 처리. */
    void refund(String orderId, long amount);
}
