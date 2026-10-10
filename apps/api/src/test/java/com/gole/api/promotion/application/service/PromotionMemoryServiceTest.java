package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.common.exception.ConflictException;
import com.gole.api.common.exception.NotFoundException;
import com.gole.api.promotion.application.port.in.ManagePromotionMemoryUseCase.*;
import com.gole.api.promotion.application.port.out.*;
import com.gole.api.promotion.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionMemoryServiceTest {
    private final PromotionFeedbackRepositoryPort feedback = mock(PromotionFeedbackRepositoryPort.class);
    private final PromotionGuidelineRepositoryPort guidelines = mock(PromotionGuidelineRepositoryPort.class);
    private final PromotionPostIdGeneratorPort ids = mock(PromotionPostIdGeneratorPort.class);
    private final PromotionMemoryService memory =
            new PromotionMemoryService(feedback, guidelines, ids, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    private PromotionFeedback source(String id, String runKey) {
        return new PromotionFeedback(
                id,
                "post1",
                "human",
                Instant.EPOCH,
                "너무 작다",
                PromotionCategory.SERVICE,
                new PromotionFeedback.Snapshot("캡션", List.of(), List.of(), null, null),
                List.of(),
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                runKey == null ? null : Instant.EPOCH,
                runKey);
    }

    private Proposal proposal(List<String> sources) {
        return new Proposal(
                PromotionGuidelineKind.PROCEDURE,
                "화면 가독성을 우선한다",
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                List.of(PromotionCategory.SERVICE),
                sources);
    }

    private PromotionGuideline proposed() {
        return new PromotionGuideline(
                "g1",
                PromotionGuidelineKind.PROCEDURE,
                "가독성",
                List.of(PromotionMemoryTarget.IMAGE_EDIT),
                List.of(PromotionCategory.SERVICE),
                List.of("f1"),
                PromotionGuidelineStatus.PROPOSED,
                "agent",
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "run-1",
                0);
    }

    @Test
    @DisplayName("수정·확정은 오래된 검토 버전과 음수 버전을 저장 전에 거부한다")
    void mutations_rejectStaleReview() {
        var current =
                proposed().edit("다른 관리자 내용", proposed().targets(), proposed().categories(), Instant.EPOCH);
        when(guidelines.findById("g1")).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> memory.edit("g1", "덮어쓰기", current.targets(), current.categories(), 0))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> memory.activate("g1", "human", 0)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> memory.activate("g1", "human", -1)).isInstanceOf(IllegalArgumentException.class);
        verify(guidelines, never()).saveIfVersion(any(), anyLong());
    }

    @Test
    @DisplayName("확정 응답 유실의 동일 확정자 재시도만 이전 버전을 허용하고 다시 저장하지 않는다")
    void activate_retriesOnlySameReviewedConfirmation() {
        var active = proposed().activate("human-a", Instant.EPOCH);
        when(guidelines.findById("g1")).thenReturn(Optional.of(active));
        assertThat(memory.activate("g1", "human-a", 0)).isEqualTo(active);
        assertThat(memory.activate("g1", "human-a", active.version())).isEqualTo(active);
        assertThatThrownBy(() -> memory.activate("g1", "human-b", 0)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> memory.activate("g1", "agent", active.version()))
                .isInstanceOf(com.gole.api.common.exception.ForbiddenException.class);
        when(guidelines.findById("g1")).thenReturn(Optional.of(active.retire(Instant.EPOCH)));
        assertThatThrownBy(() -> memory.activate("g1", "human-a", 0)).isInstanceOf(ConflictException.class);
        verify(guidelines, never()).saveIfVersion(any(), anyLong());
    }

    @Test
    @DisplayName("지침 근거는 최근 목록 범위와 무관하게 ID로 조회한다")
    void getFeedback_returnsHistoricalSource() {
        var source = source("old-feedback", "run-old");
        when(feedback.findById("old-feedback")).thenReturn(Optional.of(source));

        assertThat(memory.getFeedback("old-feedback")).isEqualTo(source);
        verify(feedback, never()).findRecent(any(), anyInt());
    }

    @Test
    @DisplayName("존재하지 않는 반려 근거는 404 도메인 예외를 반환한다")
    void getFeedback_rejectsMissingSource() {
        assertThatThrownBy(() -> memory.getFeedback("missing"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("반려 기록을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("모델 제안은 항상 PROPOSED이며 근거와 원 제안자를 고정한다")
    void reflect_createsUnconfirmedProposals() {
        when(feedback.findById("f1")).thenReturn(Optional.of(source("f1", null)));
        when(ids.newId()).thenReturn("g1");
        var result = memory.reflect(
                "agent", new ReflectionCommand(List.of("f1"), "run-1", List.of(proposal(List.of("f1")))));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(PromotionGuidelineStatus.PROPOSED);
        assertThat(result.getFirst().proposedBy()).isEqualTo("agent");
        verify(feedback).markReflected("f1", "run-1", Instant.EPOCH, true);
    }

    @Test
    @DisplayName("제안이 없어도 반려 성찰을 완료해 매 실행 반복하지 않는다")
    void reflect_emptyProposalsStillMarkFeedback() {
        when(feedback.findById("f1")).thenReturn(Optional.of(source("f1", null)));
        assertThat(memory.reflect("agent", new ReflectionCommand(List.of("f1"), "run-1", List.of())))
                .isEmpty();
        verify(feedback).markReflected("f1", "run-1", Instant.EPOCH, true);
        verify(guidelines, never()).insert(any());
    }

    @Test
    @DisplayName("같은 runKey라도 원래 묶음의 부분집합으로 재시도하면 거부한다")
    void reflect_retryRequiresExactFeedbackBatch() {
        when(feedback.findById("f1")).thenReturn(Optional.of(source("f1", "run-1")));
        when(feedback.findByReflectedRunKey("run-1")).thenReturn(List.of(source("f1", "run-1"), source("f2", "run-1")));
        assertThatThrownBy(() -> memory.reflect("agent", new ReflectionCommand(List.of("f1"), "run-1", List.of())))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(guidelines);
    }

    @Test
    @DisplayName("동일 묶음/실행의 재시도는 처음 제안을 반환하고 새로 저장하지 않는다")
    void reflect_exactRetryReturnsExisting() {
        when(feedback.findById("f1")).thenReturn(Optional.of(source("f1", "run-1")));
        when(feedback.findByReflectedRunKey("run-1")).thenReturn(List.of(source("f1", "run-1")));
        assertThat(memory.reflect("agent", new ReflectionCommand(List.of("f1"), "run-1", List.of())))
                .isEmpty();
        verify(guidelines).findByReflectionRunKey("run-1");
        verify(guidelines, never()).insert(any());
        verify(feedback, never()).markReflected(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("다른 실행이 처리한 기록이나 이번 묶음 밖의 근거를 거부한다")
    void reflect_rejectsProcessedOrUnrelatedSources() {
        when(feedback.findById("f1")).thenReturn(Optional.of(source("f1", "run-old")));
        assertThatThrownBy(() -> memory.reflect("agent", new ReflectionCommand(List.of("f1"), "run-1", List.of())))
                .isInstanceOf(ConflictException.class);
        when(feedback.findById("f1")).thenReturn(Optional.of(source("f1", null)));
        assertThatThrownBy(() -> memory.reflect(
                        "agent", new ReflectionCommand(List.of("f1"), "run-1", List.of(proposal(List.of("f2"))))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(guidelines, never()).insert(any());
    }

    @Test
    @DisplayName("기억 조회는 반려 3건/활성 지침 8개 예산을 지키고 경로 입력을 검증한다")
    void context_boundsQueriesAndValidatesRoutes() {
        memory.context(PromotionCategory.SERVICE, List.of("/market"));
        verify(feedback).findRelevant(PromotionCategory.SERVICE, List.of("/market"), 3);
        verify(guidelines).findActive(PromotionCategory.SERVICE, 8);
        verify(feedback).findUnreflected(3);
        assertThatThrownBy(() -> memory.context(PromotionCategory.SERVICE, List.of("https://other.test")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
