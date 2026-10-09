package com.gole.api.promotion.application.service;

import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionRunRepositoryPort;
import com.gole.api.promotion.domain.model.PromotionRun;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 실행 원장 기록 서비스(promotion-review D23). 봇의 기록이라 관리자 감사 로그(D7)는 남기지 않는다. */
@Service
public class PromotionRunService implements RecordPromotionRunUseCase {

    private final PromotionRunRepositoryPort runs;
    private final PromotionPostIdGeneratorPort idGenerator;
    private final Clock clock;

    public PromotionRunService(PromotionRunRepositoryPort runs, PromotionPostIdGeneratorPort idGenerator, Clock clock) {
        this.runs = runs;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public RecordedRun record(RecordRunCommand command) {
        Optional<PromotionRun> existing = runs.findByRunKey(command.runKey());
        if (existing.isPresent()) {
            // 같은 실행의 재시도다. 처음 기록이 정본이다 — 덮어쓰지 않는다.
            return new RecordedRun(existing.get(), false);
        }
        PromotionRun run = new PromotionRun(
                idGenerator.newId(),
                command.runKey(),
                command.category(),
                command.sourceCommitSha(),
                command.outcome(),
                command.reasonCode(),
                command.detail(),
                command.promotionPostId(),
                command.agentSha(),
                command.runUrl(),
                command.calls(),
                Instant.now(clock));
        PromotionRun stored = runs.insertIfAbsent(run);
        return new RecordedRun(stored, stored.id().equals(run.id()));
    }

    @Override
    public List<PromotionRun> listRecent(int limit) {
        return runs.findRecent(Math.clamp(limit, 1, MAX_LIST));
    }
}
