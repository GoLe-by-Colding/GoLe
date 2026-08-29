package com.gole.api.order.application.port.in;

import com.gole.api.order.domain.model.Order;

/**
 * 운송장 저장 전에 주문 문서에 배송 등록 펜스를 선점한다.
 *
 * <p>미발송 환불과 운송장 등록을 주문의 낙관적 락 하나로 직렬화하기 위한 order 컨텍스트의
 * 인바운드 포트다. 호출자는 배송 문서 저장까지 같은 Mongo 트랜잭션으로 감싸야 한다.
 */
public interface PrepareShipmentRegistrationUseCase {

    Order prepare(String orderId, String sellerId);
}
