package com.gole.api.order.application.port.out;

/**
 * Outbound port: 주문 상태 변화 당사자 알림. (shipping-and-fees R7.5)
 *
 * <p>자동/수동을 가리지 않고 사용자가 진행 상황을 운영자에게 묻지 않아도 알 수 있게 한다.
 * 어댑터는 notification 컨텍스트로 위임하고 실패를 흡수한다.
 */
public interface OrderEventNotifierPort {

    /** 분쟁 접수 → 판매자에게. */
    void disputeOpened(String sellerId, String orderId, String reasonLabel);

    /** 분쟁 판정 → 양 당사자에게. */
    void disputeResolved(String buyerId, String sellerId, String orderId, boolean refunded);

    /** 미발송 자동 환불 → 양 당사자에게. (R9 7일 규칙) */
    void autoRefundedForNoShipment(String buyerId, String sellerId, String orderId);

    /** 발송 독촉 → 판매자에게. (R9 3일 규칙) */
    void shipmentReminder(String sellerId, String orderId);

    /** 자동 구매확정 → 양 당사자에게. (R3.2) */
    void autoCompleted(String buyerId, String sellerId, String orderId);

    /** 구매자가 직접 구매확정 → 양 당사자에게. 구매자는 후기 작성 동선으로 연결한다. */
    void completed(String buyerId, String sellerId, String orderId);
}
