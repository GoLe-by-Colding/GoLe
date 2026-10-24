package com.gole.api.promotion.application.service;

import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CaptureOriginal;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CreatePromotionPostCommand;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionRunRepositoryPort;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 홍보 초안 납품(promotion-review D29). 기존 유스케이스(성찰·작성·검토 요청)를 러너가 부르던 순서 그대로 묶는다.
 *
 * <p>한 트랜잭션으로 묶지 않는다 — 이미지 업로드는 객체 저장소라 되돌릴 수 없다. 그래서 일을 시작하기 전에 runKey 를
 * 원장의 unique 인덱스로 먼저 선점한다. 같은 runKey 의 두 번째 요청(응답 유실 뒤 재시도·동시 요청)은 선점에 져서
 * 아무것도 하지 않고 처음 기록을 돌려받는다. 선점 기록은 {@code FAILED/ERROR DELIVERY_IN_PROGRESS} 로 두었다가 끝나면
 * 결과로 바꾼다 — 중간에 프로세스가 죽으면 그대로 남아 관리자 화면에서 보인다(초안이 둘 생기는 것보다 낫다).
 */
@Service
public class PromotionDeliveryService implements DeliverPromotionDraftUseCase {

    static final String IN_PROGRESS = "DELIVERY_IN_PROGRESS";

    private static final Logger log = LoggerFactory.getLogger(PromotionDeliveryService.class);

    private final ManagePromotionMemoryUseCase memory;
    private final ManagePromotionPostsUseCase posts;
    private final CreatePromotionPostUseCase create;
    private final SubmitPromotionPostForReviewUseCase submit;
    private final PromotionRunRepositoryPort runs;
    private final PromotionPostIdGeneratorPort ids;
    private final PromotionMediaPort media;
    private final Clock clock;

    public PromotionDeliveryService(
            ManagePromotionMemoryUseCase memory,
            ManagePromotionPostsUseCase posts,
            CreatePromotionPostUseCase create,
            SubmitPromotionPostForReviewUseCase submit,
            PromotionRunRepositoryPort runs,
            PromotionPostIdGeneratorPort ids,
            PromotionMediaPort media,
            Clock clock) {
        this.memory = memory;
        this.posts = posts;
        this.create = create;
        this.submit = submit;
        this.runs = runs;
        this.ids = ids;
        this.media = media;
        this.clock = clock;
    }

    @Override
    public PromotionRun deliver(DeliveryCommand command) {
        RecordRunCommand requested = command.run();
        PromotionRun claim = toRun(
                ids.newId(),
                result(requested, RunOutcome.FAILED, RunReasonCode.ERROR, IN_PROGRESS, null),
                Instant.now(clock));
        PromotionRun claimed = runs.insertIfAbsent(claim);
        if (!claimed.id().equals(claim.id())) {
            // 이미 받은 납품이다(재시도·동시 요청). 처음 것이 정본이다.
            return claimed;
        }
        String reflectionError = reflect(command);
        RecordRunCommand run = command.draft() == null ? requested : submitDraft(command);
        if (reflectionError != null && run.detail() == null) {
            run = result(run, run.outcome(), run.reasonCode(), reflectionError, run.promotionPostId());
        }
        return runs.replace(toRun(claim.id(), run, claim.recordedAt()));
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

    private static PromotionRun toRun(String id, RecordRunCommand run, Instant recordedAt) {
        return new PromotionRun(
                id,
                run.runKey(),
                run.category(),
                run.sourceCommitSha(),
                run.outcome(),
                run.reasonCode(),
                run.detail(),
                run.promotionPostId(),
                run.agentSha(),
                run.runUrl(),
                run.calls(),
                recordedAt,
                run.memoryContext());
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
