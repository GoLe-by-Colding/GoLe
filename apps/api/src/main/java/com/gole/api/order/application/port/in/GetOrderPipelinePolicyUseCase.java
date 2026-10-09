package com.gole.api.order.application.port.in;

import java.time.Duration;

/** Inbound port: 주문·배송 파이프라인의 예외 판정 기준 시간. 운영 화면이 파이프라인과 같은 기준으로 예외를 보게 한다. */
public interface GetOrderPipelinePolicyUseCase {

    PipelinePolicy policy();

    /**
     * @param disputeEscalationAfter 분쟁 미판정 → 운영자 에스컬레이션
     * @param carrierPickupTimeout 송장 등록 후 택배사 미접수 → 예외 큐
     * @param transitStallAfter 배송 정체 → 예외 큐
     * @param trackerUnknownAfter 트래커 연속 조회 실패 → 예외 큐
     */
    record PipelinePolicy(
            Duration disputeEscalationAfter,
            Duration carrierPickupTimeout,
            Duration transitStallAfter,
            Duration trackerUnknownAfter) {}
}
