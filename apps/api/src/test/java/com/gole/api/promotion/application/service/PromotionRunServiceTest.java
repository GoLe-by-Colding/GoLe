package com.gole.api.promotion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordRunCommand;
import com.gole.api.promotion.application.port.in.RecordPromotionRunUseCase.RecordedRun;
import com.gole.api.promotion.application.port.out.PromotionPostIdGeneratorPort;
import com.gole.api.promotion.application.port.out.PromotionRunRepositoryPort;
import com.gole.api.promotion.domain.model.PromotionCategory;
import com.gole.api.promotion.domain.model.PromotionRun;
import com.gole.api.promotion.domain.model.RunOutcome;
import com.gole.api.promotion.domain.model.RunReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromotionRunServiceTest {

    private final PromotionRunRepositoryPort runs = mock(PromotionRunRepositoryPort.class);
    private final PromotionPostIdGeneratorPort ids = mock(PromotionPostIdGeneratorPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC);
    private final PromotionRunService service = new PromotionRunService(runs, ids, clock);

    private static RecordRunCommand command() {
        return new RecordRunCommand(
                "gh-7-1",
                PromotionCategory.SERVICE,
                null,
                RunOutcome.SKIPPED,
                RunReasonCode.MODEL_SKIPPED,
                "볼 게 없음",
                null,
                null,
                null,
                List.of());
    }

    private static PromotionRun stored(String id, RunOutcome outcome, RunReasonCode reason) {
        return new PromotionRun(
                id,
                "gh-7-1",
                PromotionCategory.SERVICE,
                null,
                outcome,
                reason,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.EPOCH);
    }

    @Test
    @DisplayName("처음 기록하면 서버 시각으로 새로 남긴다")
    void record_createsNewRun() {
        when(runs.findByRunKey("gh-7-1")).thenReturn(Optional.empty());
        when(ids.newId()).thenReturn("run-1");
        when(runs.insertIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordedRun recorded = service.record(command());

        assertThat(recorded.created()).isTrue();
        assertThat(recorded.run().recordedAt()).isEqualTo(Instant.parse("2026-10-06T00:00:00Z"));
        assertThat(recorded.run().detail()).isEqualTo("볼 게 없음");
    }

    @Test
    @DisplayName("같은 runKey 로 다시 기록하면 덮어쓰지 않고 처음 것을 돌려준다")
    void record_isIdempotentByRunKey() {
        PromotionRun first = stored("run-0", RunOutcome.FAILED, RunReasonCode.ERROR);
        when(runs.findByRunKey("gh-7-1")).thenReturn(Optional.of(first));

        RecordedRun recorded = service.record(command());

        assertThat(recorded.created()).isFalse();
        assertThat(recorded.run()).isSameAs(first);
        verify(runs, never()).insertIfAbsent(any());
    }

    @Test
    @DisplayName("동시에 먼저 저장된 기록이 있으면 그것을 돌려주고 새로 만든 것으로 보지 않는다")
    void record_concurrentDuplicate_returnsStored() {
        when(runs.findByRunKey("gh-7-1")).thenReturn(Optional.empty());
        when(ids.newId()).thenReturn("run-1");
        when(runs.insertIfAbsent(any()))
                .thenReturn(stored("run-other", RunOutcome.SKIPPED, RunReasonCode.MODEL_SKIPPED));

        RecordedRun recorded = service.record(command());

        assertThat(recorded.created()).isFalse();
        assertThat(recorded.run().id()).isEqualTo("run-other");
    }

    @Test
    @DisplayName("목록 개수는 1~100 으로 맞춘다")
    void listRecent_clampsLimit() {
        service.listRecent(10_000);
        service.listRecent(0);

        verify(runs).findRecent(100);
        verify(runs).findRecent(1);
    }
}
