package com.gole.api.promotion.application.port.out;

/** 발행 에이전트를 백엔드 밖(GitHub Actions)에서 실행시킨다. 실행 결과를 기다리지 않는다. */
public interface PromotionAgentRunPort {

    void dispatchPublishRun(String requestedBy);
}
