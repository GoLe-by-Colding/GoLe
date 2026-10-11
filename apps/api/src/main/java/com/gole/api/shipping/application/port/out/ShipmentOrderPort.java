package com.gole.api.shipping.application.port.out;

import java.util.Optional;

/**
 * Outbound port: 운송장 등록 전 주문 펜스. 미발송 환불과 운송장 등록을 주문의 낙관적 락 하나로 직렬화한다. 호출자는 배송 문서 저장까지
 * 같은 트랜잭션으로 감싸야 한다.
 */
public interface ShipmentOrderPort {

    /** 펜스를 선점하고 주문 당사자를 돌려준다. 결제 승인 전 등 운송장을 받을 수 없는 주문 상태면 비어 있다. */
    Optional<ShippableOrder> fenceForRegistration(String orderId, String sellerId);

    record ShippableOrder(String id, String sellerId, String buyerId) {}
}
