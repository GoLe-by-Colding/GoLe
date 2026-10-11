package com.gole.api.order.application.port.out;

/** Outbound port: 회원 탈퇴 때 주문 기록의 차단 판정·파기를 저장소에서 한다. */
public interface OrderAccountErasurePort {

    boolean hasActiveOrder(String accountId);

    boolean hasUnsettledPayout(String accountId);
}
