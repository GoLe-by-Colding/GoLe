package com.gole.api.shipping.application.port.in;

import com.gole.api.shipping.domain.model.Shipment;

/**
 * Inbound port: 운송장 등록. (R1)
 */
public interface RegisterWaybillUseCase {

    Shipment register(RegisterWaybillCommand command);

    /**
     * @param orderId     대상 주문
     * @param sellerId    요청자(주문 판매자여야 함, R1.2)
     * @param carrierKey  택배사 키
     * @param waybill     송장번호 원문(정규화는 도메인이 수행)
     * @param sellerPhone 판매자 CS 연락처(R8.2). 재등록 시 null이면 기존 값 유지
     */
    record RegisterWaybillCommand(
            String orderId, String sellerId, String carrierKey, String waybill, String sellerPhone) {}
}
