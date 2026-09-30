package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.application.port.out.PromotionAgentRunPort;
import com.gole.api.promotion.application.port.out.PromotionPostRepositoryPort;
import com.gole.api.promotion.domain.exception.NoApprovedPromotionPostsException;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionPublishRunServiceTest {

    private final PromotionPostRepositoryPort repository = mock(PromotionPostRepositoryPort.class);
    private final PromotionAgentRunPort agentRuns = mock(PromotionAgentRunPort.class);
    private final PromotionPublishRunService service = new PromotionPublishRunService(repository, agentRuns);

    @Test
    @DisplayName("승인된 글이 없으면 에이전트를 띄우지 않고 거절한다")
    void requestPublishRun_rejectsWhenNothingApproved() {
        when(repository.countByStatus(PromotionPostStatus.APPROVED)).thenReturn(0L);

        assertThatThrownBy(() -> service.requestPublishRun("admin@gole.test"))
                .isInstanceOf(NoApprovedPromotionPostsException.class);
        verify(agentRuns, never()).dispatchPublishRun(any());
    }

    @Test
    @DisplayName("승인된 글이 있으면 요청자와 함께 발행 실행을 시작한다")
    void requestPublishRun_dispatchesWhenApprovedExists() {
        when(repository.countByStatus(PromotionPostStatus.APPROVED)).thenReturn(2L);

        service.requestPublishRun("admin@gole.test");

        verify(agentRuns).dispatchPublishRun("admin@gole.test");
    }
}
