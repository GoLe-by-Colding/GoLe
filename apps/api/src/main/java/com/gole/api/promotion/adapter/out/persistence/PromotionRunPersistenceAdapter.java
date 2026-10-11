package com.gole.api.promotion.adapter.out.persistence;

import com.gole.api.promotion.adapter.out.persistence.PromotionRunDocument.CallDocument;
import com.gole.api.promotion.application.port.out.PromotionRunRepositoryPort;
import com.gole.api.promotion.domain.model.ModelCall;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionGuidelineKind;
import com.gole.api.promotion.domain.model.PromotionMemoryContext;
import com.gole.api.promotion.domain.model.PromotionMemoryTarget;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** 실행 원장 영속성 어댑터. 도메인 {@link PromotionRun} 과 {@link PromotionRunDocument} 를 양방향 매핑한다. */
@Component
public class PromotionRunPersistenceAdapter implements PromotionRunRepositoryPort {

    private final PromotionRunMongoRepository repository;

    public PromotionRunPersistenceAdapter(PromotionRunMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PromotionRun> findByRunKey(String runKey) {
        return repository.findByRunKey(runKey).map(PromotionRunPersistenceAdapter::toDomain);
    }

    @Override
    public PromotionRun insertIfAbsent(PromotionRun run) {
        try {
            // save 가 아니라 insert — 같은 runKey 를 덮어쓰지 않고 unique 인덱스 충돌로 드러나게 한다.
            return toDomain(repository.insert(toDocument(run)));
        } catch (DuplicateKeyException duplicate) {
            return findByRunKey(run.runKey()).orElseThrow(() -> duplicate);
        }
    }

    @Override
    public List<PromotionRun> findRecent(int limit) {
        return repository.findAllByOrderByRecordedAtDesc(PageRequest.of(0, limit)).stream()
                .map(PromotionRunPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<PromotionRun> findAll() {
        return repository.findAll().stream()
                .map(PromotionRunPersistenceAdapter::toDomain)
                .toList();
    }

    private static PromotionRunDocument toDocument(PromotionRun run) {
        PromotionRunDocument document = new PromotionRunDocument(
                run.id(),
                run.runKey(),
                run.category().name(),
                run.sourceCommitSha(),
                run.outcome().name(),
                run.reasonCode().name(),
                run.detail(),
                run.promotionPostId(),
                run.agentSha(),
                run.runUrl(),
                run.calls().stream()
                        .map(call -> new CallDocument(
                                call.engine(),
                                call.model(),
                                call.ok(),
                                call.inputTokens(),
                                call.cachedInputTokens(),
                                call.outputTokens(),
                                call.costUsd(),
                                call.durationMs()))
                        .toList(),
                run.recordedAt());
        document.setMemoryContext(new PromotionRunDocument.MemoryContextDocument(
                run.memoryContext().feedbackIds(),
                run.memoryContext().guidelines().stream()
                        .map(guideline -> new PromotionRunDocument.GuidelineSnapshotDocument(
                                guideline.id(),
                                guideline.kind().name(),
                                guideline.content(),
                                guideline.targets().stream().map(Enum::name).toList(),
                                guideline.categories().stream().map(Enum::name).toList()))
                        .toList()));
        return document;
    }

    private static PromotionRun toDomain(PromotionRunDocument document) {
        List<CallDocument> calls = document.getCalls() == null ? List.of() : document.getCalls();
        return new PromotionRun(
                document.getId(),
                document.getRunKey(),
                PromotionCategory.valueOf(document.getCategory()),
                document.getSourceCommitSha(),
                RunOutcome.valueOf(document.getOutcome()),
                RunReasonCode.valueOf(document.getReasonCode()),
                document.getDetail(),
                document.getPromotionPostId(),
                document.getAgentSha(),
                document.getRunUrl(),
                calls.stream()
                        .map(call -> new ModelCall(
                                call.engine(),
                                call.model(),
                                call.ok(),
                                call.inputTokens(),
                                call.cachedInputTokens(),
                                call.outputTokens(),
                                call.costUsd(),
                                call.durationMs()))
                        .toList(),
                document.getRecordedAt(),
                document.getMemoryContext() == null
                        ? PromotionMemoryContext.EMPTY
                        : new PromotionMemoryContext(
                                document.getMemoryContext().feedbackIds(),
                                document.getMemoryContext().guidelines().stream()
                                        .map(guideline -> new PromotionMemoryContext.GuidelineSnapshot(
                                                guideline.id(),
                                                PromotionGuidelineKind.valueOf(guideline.kind()),
                                                guideline.content(),
                                                guideline.targets().stream()
                                                        .map(PromotionMemoryTarget::valueOf)
                                                        .toList(),
                                                guideline.categories().stream()
                                                        .map(PromotionCategory::valueOf)
                                                        .toList()))
                                        .toList()));
    }
}
