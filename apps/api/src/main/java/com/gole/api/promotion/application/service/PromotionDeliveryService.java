package com.gole.api.promotion.application.service;

import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CaptureOriginal;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CreatePromotionPostCommand;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import com.gole.api.promotion.application.port.out.PromotionRunRepositoryPort;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 홍보 초안 납품(promotion-review D29). 기존 유스케이스(성찰·작성·검토 요청·원장)를 러너가 부르던 순서 그대로 묶는다.
 *
 * <p>한 트랜잭션으로 묶지 않는다 — 이미지 업로드는 객체 저장소라 되돌릴 수 없고, 러너 경로와 같은 단위로 저장돼야
 * 실패 지점이 같은 모양으로 원장에 남는다. 어디서 실패하든 원장 한 줄은 남긴다.
 */
@Service
public class PromotionDeliveryService implements DeliverPromotionDraftUseCase {

    private static final Logger log = LoggerFactory.getLogger(PromotionDeliveryService.class);

    private final ManagePromotionMemoryUseCase memory;
    private final ManagePromotionPostsUseCase posts;
    private final CreatePromotionPostUseCase create;
    private final SubmitPromotionPostForReviewUseCase submit;
    private final RecordPromotionRunUseCase runs;
    private final PromotionRunRepositoryPort runRepository;
    private final PromotionMediaPort media;

    public PromotionDeliveryService(
            ManagePromotionMemoryUseCase memory,
            ManagePromotionPostsUseCase posts,
            CreatePromotionPostUseCase create,
            SubmitPromotionPostForReviewUseCase submit,
            RecordPromotionRunUseCase runs,
            PromotionRunRepositoryPort runRepository,
            PromotionMediaPort media) {
        this.memory = memory;
        this.posts = posts;
        this.create = create;
        this.submit = submit;
        this.runs = runs;
        this.runRepository = runRepository;
        this.media = media;
    }

    @Override
    public PromotionRun deliver(DeliveryCommand command) {
        Optional<PromotionRun> delivered =
                runRepository.findByRunKey(command.run().runKey());
        if (delivered.isPresent()) {
            return delivered.get();
        }
        String reflectionError = reflect(command);
        RecordRunCommand run = command.draft() == null ? command.run() : submitDraft(command);
        if (reflectionError != null && run.detail() == null) {
            run = result(run, run.outcome(), run.reasonCode(), reflectionError, run.promotionPostId());
        }
        return runs.record(run).run();
    }

    /** 성찰 실패는 초안을 막지 않는다 — 다음 실행이 미처리 반려를 다시 읽는다(러너와 같다). */
    private String reflect(DeliveryCommand command) {
        if (command.reflection() == null) {
            return null;
        }
        try {
            memory.reflect(command.actorId(), command.reflection());
            return null;
        } catch (RuntimeException failed) {
            log.warn(
                    "[promotion-delivery] reflection failed runKey={} error={}",
                    command.run().runKey(),
                    failed.getClass().getSimpleName());
            return "MEMORY_REFLECTION_FAILED";
        }
    }

    private RecordRunCommand submitDraft(DeliveryCommand command) {
        RecordRunCommand run = command.run();
        DraftDelivery draft = command.draft();
        if (posts.list(PromotionPostStatus.PENDING_REVIEW, MAX_PENDING_REVIEW + 1)
                        .size()
                >= MAX_PENDING_REVIEW) {
            return result(run, RunOutcome.SKIPPED, RunReasonCode.QUEUE_FULL, null, null);
        }
        if (run.sourceCommitSha() != null && posts.existsBySourceCommitSha(run.sourceCommitSha())) {
            return result(run, RunOutcome.SKIPPED, RunReasonCode.ALREADY_DRAFTED, null, null);
        }
        try {
            List<String> mediaKeys = new ArrayList<>();
            List<CaptureOriginal> originals = new ArrayList<>();
            for (DeliveredImage image : draft.images()) {
                mediaKeys.add(upload(command.actorId(), image.image()));
                // 원본 없이 올린 사진 자리는 null 이다(CreatePromotionPostCommand 규약).
                originals.add(
                        image.original() == null
                                ? null
                                : new CaptureOriginal(upload(command.actorId(), image.original()), image.edit()));
            }
            String postId = create.create(new CreatePromotionPostCommand(
                    command.actorId(),
                    draft.channel(),
                    draft.caption(),
                    mediaKeys,
                    run.sourceCommitSha(),
                    draft.context(),
                    originals.stream().allMatch(original -> original == null) ? List.of() : originals));
            submit.submit(postId);
            return result(run, RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED, run.detail(), postId);
        } catch (RuntimeException failed) {
            // 원문에는 내부 사정이 섞일 수 있다. 원장에는 예외 이름만 남긴다.
            log.warn(
                    "[promotion-delivery] draft failed runKey={} error={}",
                    run.runKey(),
                    failed.getClass().getSimpleName());
            return result(
                    run,
                    RunOutcome.FAILED,
                    RunReasonCode.ERROR,
                    "DELIVERY_FAILED " + failed.getClass().getSimpleName(),
                    null);
        }
    }

    private String upload(String ownerId, Image image) {
        return media.upload(ownerId, image.content(), image.contentType(), image.filename());
    }

    private static RecordRunCommand result(
            RecordRunCommand run, RunOutcome outcome, RunReasonCode reasonCode, String detail, String postId) {
        return new RecordRunCommand(
                run.runKey(),
                run.category(),
                run.sourceCommitSha(),
                outcome,
                reasonCode,
                detail,
                postId,
                run.agentSha(),
                run.runUrl(),
                run.calls(),
                run.memoryContext());
    }
}
