package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.OrderAccountErasureUseCase;
import com.gole.api.order.application.port.out.OrderAccountErasurePort;
import org.springframework.stereotype.Service;

/** 회원 탈퇴 때 주문 기록 처리. 판정과 파기는 저장소 포트가 하고, 트랜잭션은 호출자(계정 파기)의 것을 쓴다. */
@Service
public class OrderAccountErasureService implements OrderAccountErasureUseCase {

    private final OrderAccountErasurePort erasure;

    public OrderAccountErasureService(OrderAccountErasurePort erasure) {
        this.erasure = erasure;
    }

    @Override
    public boolean hasActiveOrder(String accountId) {
        return erasure.hasActiveOrder(accountId);
    }

    @Override
    public boolean hasUnsettledPayout(String accountId) {
        return erasure.hasUnsettledPayout(accountId);
    }
}
