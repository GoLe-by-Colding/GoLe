package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.PaymentMethod;

/**
 * Outbound port: 결제 게이트웨이(PG) 연동. 자금 보유(authorize) 및 환불.
 * (요구사항 13.2, 13.3, 13.6)
 */
public interface PaymentGatewayPort {

    /**
     * 결제 승인(자금 보유)을 검증한다.
     *
     * <p>승인 여부뿐 아니라 <b>무엇으로 결제됐는지</b>를 함께 돌려준다. 결제수단은 승인 시점에만
     * PG가 알려주는 사실이라, 여기서 버리면 되찾을 곳이 없다.
     */
    PaymentAuthorization authorize(String orderId, long amount);

    /** 환불 처리. */
    void refund(String orderId, long amount);

    /**
     * 결제 승인 결과.
     *
     * @param approved 승인(자금 보유) 성공 여부
     * @param method 승인된 결제수단. 거절이거나 PG가 알려주지 않으면 null.
     */
    record PaymentAuthorization(boolean approved, PaymentMethod method) {

        public static PaymentAuthorization approved(PaymentMethod method) {
            return new PaymentAuthorization(true, method);
        }

        public static PaymentAuthorization declined() {
            return new PaymentAuthorization(false, null);
        }
    }
}
