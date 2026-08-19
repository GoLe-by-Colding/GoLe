package com.gole.api.shipping.application.port.out;

/**
 * Outbound port: 배송 이벤트 알림. 어댑터가 notification 컨텍스트로 위임하며
 * 실패를 흡수한다(알림 실패가 배송 흐름을 막지 않는다).
 */
public interface ShipmentNotifierPort {

    /** 운송장 등록 → 구매자에게. (R1.5) */
    void notifyWaybillRegistered(String buyerId, String orderId, String carrierLabel, String waybillNumber);

    /** 배송 완료 → 구매자·판매자 모두에게. (R2.4) */
    void notifyDelivered(String buyerId, String sellerId, String orderId);
}
