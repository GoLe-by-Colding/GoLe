package com.gole.api.order.application.port.out;

import com.gole.api.order.domain.model.PaymentMethod;

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
    PaymentVerification verifyPayment(String orderId, long amount);

    /** 환불 처리. PG가 비동기 접수만 한 경우 REQUESTED를 반환한다. */
    RefundResult refund(String orderId, long amount);

    /** PG 원장 재조회로 전액 환불 완료 여부를 확인한다. */
    boolean isFullyRefunded(String orderId, long amount);

    /**
     * 결제 검증 결과.
     *
     * <p>상태뿐 아니라 <b>무엇으로 결제됐는지</b>를 함께 돌려준다. 결제수단은 승인된 결제 원장에만
     * 실려오는 사실이라 여기서 버리면 되찾을 곳이 없다. 이미 원장 검증이 {@code method}를 읽고
     * 있으므로 추가 조회도 필요 없다.
     *
     * @param result 검증 상태
     * @param method 확인된 결제수단. {@code PAID}가 아니거나 PG가 알려주지 않으면 null.
     */
    record PaymentVerification(PaymentVerificationResult result, PaymentMethod method) {

        /** 결제수단을 확인할 수 없는 결과(대기·실패·검토 등). */
        public static PaymentVerification of(PaymentVerificationResult result) {
            return new PaymentVerification(result, null);
        }

        public static PaymentVerification paid(PaymentMethod method) {
            return new PaymentVerification(PaymentVerificationResult.PAID, method);
        }
    }
}
