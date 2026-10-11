package com.gole.api.promotion.application.port.in;

import com.gole.api.promotion.domain.model.ModelCall;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionMemoryContext;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.util.List;

/**
 * 홍보 초안 에이전트 실행 원장 기록·조회 유스케이스(관리자 전용, promotion-review D23).
 * 같은 {@code runKey} 로 다시 기록하면 새로 만들지 않고 처음 것을 돌려준다 — 재시도해도 1줄이다.
 */
public interface RecordPromotionRunUseCase {

    int MAX_LIST = 100;

    RecordedRun record(RecordRunCommand command);

    /** 최근 기록부터 {@code limit}건. 1~{@value #MAX_LIST} 로 맞춘다. */
    List<PromotionRun> listRecent(int limit);

    /** @param created 이번 요청으로 새로 남겼으면 true, 이미 있던 기록이면 false */
    record RecordedRun(PromotionRun run, boolean created) {}

    record RecordRunCommand(
            String runKey,
            PromotionCategory category,
            String sourceCommitSha,
            RunOutcome outcome,
            RunReasonCode reasonCode,
            String detail,
            String promotionPostId,
            String agentSha,
            String runUrl,
            List<ModelCall> calls,
            PromotionMemoryContext memoryContext) {
        public RecordRunCommand(
                String runKey,
                PromotionCategory category,
                String sourceCommitSha,
                RunOutcome outcome,
                RunReasonCode reasonCode,
                String detail,
                String promotionPostId,
                String agentSha,
                String runUrl,
                List<ModelCall> calls) {
            this(
                    runKey,
                    category,
                    sourceCommitSha,
                    outcome,
                    reasonCode,
                    detail,
                    promotionPostId,
                    agentSha,
                    runUrl,
                    calls,
                    PromotionMemoryContext.EMPTY);
        }
    }
}
