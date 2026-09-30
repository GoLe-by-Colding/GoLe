package com.gole.api.promotion.application.service;

import com.gole.api.promotion.application.port.in.RequestPromotionPublishRunUseCase;
import com.gole.api.promotion.application.port.out.PromotionAgentRunPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.domain.exception.NoApprovedPromotionPostsException;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import org.springframework.stereotype.Service;

@Service
public class PromotionPublishRunService implements RequestPromotionPublishRunUseCase {

    private final PromotionPostRepositoryPort repository;
    private final PromotionAgentRunPort agentRuns;

    public PromotionPublishRunService(PromotionPostRepositoryPort repository, PromotionAgentRunPort agentRuns) {
        this.repository = repository;
        this.agentRuns = agentRuns;
    }

    @Override
    public void requestPublishRun(String requestedBy) {
        // 고를 글이 없으면 유료 실행을 띄우지 않고 바로 알려준다.
        if (repository.countByStatus(PromotionPostStatus.APPROVED) == 0) {
            throw new NoApprovedPromotionPostsException();
        }
        agentRuns.dispatchPublishRun(requestedBy);
    }
}
