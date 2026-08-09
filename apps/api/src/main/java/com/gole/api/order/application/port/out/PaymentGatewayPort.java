package com.gole.api.order.application.port.out;

/**
 * Outbound port: 결제 게이트웨이(PG) 연동. 자금 보유(authorize) 및 환불.
 * (요구사항 13.2, 13.3, 13.6)
 */
public interface PaymentGatewayPort {

    enum PaymentVerificationResult {
        PAID,
        PENDING,
        /** PG 원장에 아직 결제 건 자체가 없다. 사용자의 즉시 조회에서는 실패로 확정하지 않는다. */
        NOT_FOUND,
        FAILED,
        REVIEW_REQUIRED
    }

    enum RefundResult {
        SUCCEEDED,
        REQUESTED
    }

    /** 브라우저 결제창을 열기 전에 주문 금액을 PG에 고정한다. 스텁은 아무 작업도 하지 않는다. */
    default void preparePayment(String orderId, long amount) {}

    /** PG 원장을 조회해 결제의 최종/진행 상태를 검증한다. */
    PaymentVerificationResult verifyPayment(String orderId, long amount);

    /** 환불 처리. PG가 비동기 접수만 한 경우 REQUESTED를 반환한다. */
    RefundResult refund(String orderId, long amount);

    /** PG 원장 재조회로 전액 환불 완료 여부를 확인한다. */
    boolean isFullyRefunded(String orderId, long amount);
}
