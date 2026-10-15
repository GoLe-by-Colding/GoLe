package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CaptureOriginal;
import com.gole.api.promotion.application.port.in.CreatePromotionPostUseCase.CreatePromotionPostCommand;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveredImage;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DeliveryCommand;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.DraftDelivery;
import com.gole.api.promotion.application.port.in.DeliverPromotionDraftUseCase.Image;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase.ReflectionCommand;
import com.gole.api.promotion.application.port.in.ManagePromotionPostsUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordedRun;
import com.gole.api.promotion.application.port.in.SubmitPromotionPostForReviewUseCase;
import com.gole.api.promotion.application.port.out.PromotionMediaPort;
import com.gole.api.promotion.application.port.out.PromotionRunRepositoryPort;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionChannel;
import com.gole.api.promotion.domain.model.PromotionPost;
import com.gole.api.promotion.domain.model.PromotionPostContext;
import com.gole.api.promotion.domain.model.PromotionPostStatus;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PromotionDeliveryServiceTest {

    private static final String BOT = "bot-1";
    private static final String SHA = "a".repeat(40);

    private final ManagePromotionMemoryUseCase memory = mock(ManagePromotionMemoryUseCase.class);
    private final ManagePromotionPostsUseCase posts = mock(ManagePromotionPostsUseCase.class);
    private final CreatePromotionPostUseCase create = mock(CreatePromotionPostUseCase.class);
    private final SubmitPromotionPostForReviewUseCase submit = mock(SubmitPromotionPostForReviewUseCase.class);
    private final RecordPromotionRunUseCase runs = mock(RecordPromotionRunUseCase.class);
    private final PromotionRunRepositoryPort runRepository = mock(PromotionRunRepositoryPort.class);
    private final PromotionMediaPort media = mock(PromotionMediaPort.class);
    private final PromotionDeliveryService service =
            new PromotionDeliveryService(memory, posts, create, submit, runs, runRepository, media);

    private static final Image PNG = new Image(new byte[] {1}, "image/png", "promotion.png");

    private static RecordRunCommand run(RunOutcome outcome, RunReasonCode reason) {
        return new RecordRunCommand(
                "gh-9-1", PromotionCategory.FEATURE, SHA, outcome, reason, null, null, null, null, List.of());
    }

    private static DraftDelivery draft(DeliveredImage image) {
        return new DraftDelivery(PromotionChannel.THREADS, "캡션", PromotionPostContext.NONE, List.of(image));
    }

    private static PromotionRun stored(RunOutcome outcome, RunReasonCode reason) {
        return new PromotionRun(
                "run-1",
                "gh-9-1",
                PromotionCategory.FEATURE,
                SHA,
                outcome,
                reason,
                null,
                outcome == RunOutcome.SUBMITTED ? "post-1" : null,
                null,
                null,
                List.of(),
                Instant.EPOCH);
    }

    private RecordRunCommand recorded() {
        ArgumentCaptor<RecordRunCommand> captor = ArgumentCaptor.forClass(RecordRunCommand.class);
        verify(runs).record(captor.capture());
        return captor.getValue();
    }

    private void emptyQueue() {
        when(runRepository.findByRunKey("gh-9-1")).thenReturn(Optional.empty());
        when(posts.list(eq(PromotionPostStatus.PENDING_REVIEW), anyInt())).thenReturn(List.of());
        when(runs.record(any()))
                .thenReturn(new RecordedRun(stored(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED), true));
    }

    @Test
    @DisplayName("초안 납품: 이미지·원본을 올리고 초안을 만들어 검토 요청한 뒤 SUBMITTED 원장을 남긴다")
    void deliver_uploadsCreatesSubmitsAndRecords() {
        emptyQueue();
        when(media.upload(eq(BOT), any(), any(), any())).thenReturn("images/polished.png", "images/original.png");
        when(create.create(any())).thenReturn("post-1");

        service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                null,
                draft(new DeliveredImage(PNG, PNG, "다듬기 지시"))));

        ArgumentCaptor<CreatePromotionPostCommand> created = ArgumentCaptor.forClass(CreatePromotionPostCommand.class);
        verify(create).create(created.capture());
        assertThat(created.getValue().authorId()).isEqualTo(BOT);
        assertThat(created.getValue().mediaKeys()).containsExactly("images/polished.png");
        assertThat(created.getValue().originals())
                .containsExactly(new CaptureOriginal("images/original.png", "다듬기 지시"));
        verify(submit).submit("post-1");
        assertThat(recorded().outcome()).isEqualTo(RunOutcome.SUBMITTED);
        assertThat(recorded().promotionPostId()).isEqualTo("post-1");
    }

    @Test
    @DisplayName("원본 없이 올린 사진뿐이면 originals 를 비운다")
    void deliver_withoutOriginals_sendsNoOriginals() {
        emptyQueue();
        when(media.upload(eq(BOT), any(), any(), any())).thenReturn("images/raw.png");
        when(create.create(any())).thenReturn("post-1");

        service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                null,
                draft(new DeliveredImage(PNG, null, null))));

        ArgumentCaptor<CreatePromotionPostCommand> created = ArgumentCaptor.forClass(CreatePromotionPostCommand.class);
        verify(create).create(created.capture());
        assertThat(created.getValue().originals()).isEmpty();
    }

    @Test
    @DisplayName("주문 뒤 검토 큐가 찼으면 아무것도 올리지 않고 QUEUE_FULL 로 남긴다")
    void deliver_queueFullAtDelivery_skips() {
        emptyQueue();
        when(posts.list(eq(PromotionPostStatus.PENDING_REVIEW), anyInt()))
                .thenReturn(Collections.nCopies(5, mock(PromotionPost.class)));

        service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                null,
                draft(new DeliveredImage(PNG, null, null))));

        verifyNoInteractions(media, create, submit);
        assertThat(recorded().outcome()).isEqualTo(RunOutcome.SKIPPED);
        assertThat(recorded().reasonCode()).isEqualTo(RunReasonCode.QUEUE_FULL);
    }

    @Test
    @DisplayName("주문 뒤 같은 릴리스 초안이 생겼으면 ALREADY_DRAFTED 로 남긴다")
    void deliver_alreadyDraftedAtDelivery_skips() {
        emptyQueue();
        when(posts.existsBySourceCommitSha(SHA)).thenReturn(true);

        service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                null,
                draft(new DeliveredImage(PNG, null, null))));

        verifyNoInteractions(media, create);
        assertThat(recorded().reasonCode()).isEqualTo(RunReasonCode.ALREADY_DRAFTED);
    }

    @Test
    @DisplayName("업로드·작성이 실패해도 원장은 남긴다 — 예외 이름만 detail 에 싣는다")
    void deliver_failureStillRecordsRun() {
        emptyQueue();
        when(media.upload(any(), any(), any(), any())).thenThrow(new IllegalStateException("저장소 주소 노출"));

        service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                null,
                draft(new DeliveredImage(PNG, null, null))));

        assertThat(recorded().outcome()).isEqualTo(RunOutcome.FAILED);
        assertThat(recorded().reasonCode()).isEqualTo(RunReasonCode.ERROR);
        assertThat(recorded().detail()).isEqualTo("DELIVERY_FAILED IllegalStateException");
    }

    @Test
    @DisplayName("초안 없는 납품(건너뜀·실패)은 원장만 남기고 성찰은 저장한다")
    void deliver_withoutDraft_recordsOnlyRunAndReflects() {
        emptyQueue();
        ReflectionCommand reflection = new ReflectionCommand(List.of("fb-1"), "gh-9-1", List.of());

        service.deliver(
                new DeliveryCommand(BOT, run(RunOutcome.SKIPPED, RunReasonCode.MODEL_SKIPPED), reflection, null));

        verify(memory).reflect(BOT, reflection);
        verifyNoInteractions(media, create, submit);
        assertThat(recorded().reasonCode()).isEqualTo(RunReasonCode.MODEL_SKIPPED);
    }

    @Test
    @DisplayName("성찰 저장 실패는 초안을 막지 않고 detail 로만 남는다")
    void deliver_reflectionFailure_doesNotBlockDraft() {
        emptyQueue();
        when(memory.reflect(any(), any())).thenThrow(new IllegalArgumentException("x"));
        when(media.upload(any(), any(), any(), any())).thenReturn("images/raw.png");
        when(create.create(any())).thenReturn("post-1");

        service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                new ReflectionCommand(List.of("fb-1"), "gh-9-1", List.of()),
                draft(new DeliveredImage(PNG, null, null))));

        assertThat(recorded().outcome()).isEqualTo(RunOutcome.SUBMITTED);
        assertThat(recorded().detail()).isEqualTo("MEMORY_REFLECTION_FAILED");
    }

    @Test
    @DisplayName("같은 runKey 재납품은 아무것도 하지 않고 처음 원장을 돌려준다 — 초안이 둘 생기지 않는다")
    void deliver_sameRunKey_isIdempotent() {
        PromotionRun first = stored(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED);
        when(runRepository.findByRunKey("gh-9-1")).thenReturn(Optional.of(first));

        PromotionRun result = service.deliver(new DeliveryCommand(
                BOT,
                run(RunOutcome.SUBMITTED, RunReasonCode.SUBMITTED),
                new ReflectionCommand(List.of("fb-1"), "gh-9-1", List.of()),
                draft(new DeliveredImage(PNG, null, null))));

        assertThat(result).isSameAs(first);
        verifyNoInteractions(memory, media, create, submit);
        verify(runs, never()).record(any());
    }
}
