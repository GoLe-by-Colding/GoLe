package com.gole.api.order.application.port.in;

/**
 * Inbound port: 분쟁 판정. (shipping-and-fees R4.4, R9.2)
 *
 * <p>금전 귀속을 기계가 단정하면 안 되므로 <b>사람(운영자)만</b> 호출한다 —
 * 무개입 파이프라인의 유일한 의도적 예외다.
 */
public interface ResolveDisputeUseCase {

    void resolve(ResolveDisputeCommand command);

    enum Resolution {
        /** 구매자 승 — 전액 환불, 수수료 없음(R5.5). */
        REFUND,
        /** 판매자 승 — 거래 완료, 수수료 확정 + 정산. */
        COMPLETE
    }

    record ResolveDisputeCommand(String orderId, Resolution resolution) {}
}
