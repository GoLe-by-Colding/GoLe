package com.gole.api.common.operations;

/** 운영 이벤트 출력 포트. 구현 실패가 비즈니스 요청을 실패시키면 안 된다. */
public interface OperationalEventPublisher {

    void publish(OperationalEvent event);
}
