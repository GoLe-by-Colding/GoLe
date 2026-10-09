package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.GetOrderPipelinePolicyUseCase;
import com.gole.api.order.application.service.pipeline.PipelineProperties;
import org.springframework.stereotype.Service;

/** 파이프라인 설정({@code gole.pipeline.*})에서 예외 판정 기준 시간을 내준다. */
@Service
public class OrderPipelinePolicyService implements GetOrderPipelinePolicyUseCase {

    private final PipelineProperties properties;

    public OrderPipelinePolicyService(PipelineProperties properties) {
        this.properties = properties;
    }

    @Override
    public PipelinePolicy policy() {
        return new PipelinePolicy(
                properties.disputeEscalationAfter(),
                properties.carrierPickupTimeout(),
                properties.transitStallAfter(),
                properties.trackerUnknownAfter());
    }
}
