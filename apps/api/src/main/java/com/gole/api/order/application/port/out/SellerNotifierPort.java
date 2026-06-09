package com.gole.api.order.application.port.out;

/**
 * Outbound port: 셀러 알림. 주문 생성 등 이벤트를 셀러에게 알린다(best-effort).
 * 구현 어댑터가 notification 컨텍스트의 인바운드 포트로 위임한다. (알림 스펙 N6)
 */
public interface SellerNotifierPort {

    /** 매물에 주문이 들어왔음을 셀러에게 알린다. 실패해도 주문 흐름을 막지 않는다. */
    void notifyOrderPlaced(String sellerId, String orderId, long amount);
}
