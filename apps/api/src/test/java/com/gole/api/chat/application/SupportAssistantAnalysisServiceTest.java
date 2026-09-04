package com.gole.api.chat.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort.Claim;
import com.gole.api.chat.application.port.out.SupportAssistantPort;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Priority;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import com.gole.api.chat.application.port.out.SupportAssistantWorkSourcePort;
import com.gole.api.chat.domain.model.SupportCategory;
import com.gole.api.chat.domain.model.SupportTicket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class SupportAssistantAnalysisServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T01:02:03Z");
    private static final Instant LEASE_UNTIL = Instant.parse("2026-09-04T01:02:33Z");
    private static final String LEASE_TOKEN = "lease-1";

    private final SupportAssistantPort assistant = mock(SupportAssistantPort.class);
    private final SupportAssistantAnalysisRepositoryPort analyses = mock(SupportAssistantAnalysisRepositoryPort.class);
    private final SupportAssistantWorkSourcePort sources = mock(SupportAssistantWorkSourcePort.class);
    private final SupportAssistantAnalysisService service = serviceWith(Runnable::run);

    @Test
    void openingIsPersistedAndAnalyzedOnlyAfterCommit() {
        Analysis result = result();
        when(analyses.tryClaim("room-1", NOW, LEASE_UNTIL, 5))
                .thenReturn(Optional.of(new Claim("room-1", LEASE_TOKEN, 1)));
        when(assistant.analyze(any())).thenReturn(Optional.of(result));
        beginTransactionSynchronization();
        try {
            service.analyzeOpeningAfterCommit(ticket(), "민감한 제목", "민감한 문의 본문", "ko-KR");

            verifyNoInteractions(assistant, analyses);

            TransactionSynchronizationUtils.triggerAfterCommit();

            verify(analyses).enqueue("room-1", NOW);
            verify(assistant)
                    .analyze(new Request("room-1", SupportCategory.PRODUCT_FEEDBACK, "민감한 제목", "민감한 문의 본문", "ko-KR"));
            verify(analyses).complete("room-1", LEASE_TOKEN, result, NOW);
            verifyNoInteractions(sources);
        } finally {
            clearTransactionSynchronization();
        }
    }

    @Test
    void rollbackDoesNotRegisterOrRunAnalysis() {
        beginTransactionSynchronization();
        try {
            service.analyzeOpeningAfterCommit(ticket(), "제목", "본문", "ko-KR");

            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verifyNoInteractions(assistant, analyses, sources);
        } finally {
            clearTransactionSynchronization();
        }
    }

    @Test
    void queueRejectionLeavesDurablePendingWorkForScheduler() {
        SupportAssistantAnalysisService rejecting = serviceWith(task -> {
            throw new TaskRejectedException("queue full");
        });

        assertThatCode(() -> rejecting.analyzeOpeningAfterCommit(ticket(), "제목", "본문", "ko-KR"))
                .doesNotThrowAnyException();

        verify(analyses).enqueue("room-1", NOW);
        verify(analyses, never()).tryClaim(any(), any(), any(), any(Integer.class));
        verifyNoInteractions(assistant, sources);
    }

    @Test
    void transientGrpcFailureIsRetriedWithBackoffWithoutRawContentInState() {
        when(analyses.tryClaim("room-1", NOW, LEASE_UNTIL, 5))
                .thenReturn(Optional.of(new Claim("room-1", LEASE_TOKEN, 1)));
        when(assistant.analyze(any())).thenThrow(new IllegalStateException("민감한 본문이 포함된 내부 오류"));

        assertThatCode(() -> service.analyzeOpeningAfterCommit(ticket(), "민감한 제목", "민감한 본문", "ko-KR"))
                .doesNotThrowAnyException();

        verify(analyses).retry("room-1", LEASE_TOKEN, NOW, NOW.plusSeconds(5));
        verify(analyses, never()).fail(any(), any(), any());
        verify(analyses, never()).complete(any(), any(), any(), any());
    }

    @Test
    void recoveryRebuildsRequestFromExistingRoomAndOpeningMessage() {
        Request persisted = new Request("room-1", SupportCategory.PRODUCT_FEEDBACK, "저장된 제목", "저장된 본문", "ko-KR");
        Analysis result = result();
        when(analyses.findRecoverableRoomIds(NOW, 5, 50)).thenReturn(List.of("room-1"));
        when(analyses.tryClaim("room-1", NOW, LEASE_UNTIL, 5))
                .thenReturn(Optional.of(new Claim("room-1", LEASE_TOKEN, 2)));
        when(sources.findRequest("room-1")).thenReturn(Optional.of(persisted));
        when(assistant.analyze(persisted)).thenReturn(Optional.of(result));

        service.recoverDueWork();

        verify(analyses).complete("room-1", LEASE_TOKEN, result, NOW);
    }

    @Test
    void unavailableSourceRetriesInsteadOfLeavingProcessingForever() {
        when(analyses.findRecoverableRoomIds(NOW, 5, 50)).thenReturn(List.of("room-1"));
        when(analyses.tryClaim("room-1", NOW, LEASE_UNTIL, 5))
                .thenReturn(Optional.of(new Claim("room-1", LEASE_TOKEN, 3)));
        when(sources.findRequest("room-1")).thenReturn(Optional.empty());

        service.recoverDueWork();

        verify(analyses).retry("room-1", LEASE_TOKEN, NOW, NOW.plusSeconds(20));
        verifyNoInteractions(assistant);
    }

    @Test
    void finalAttemptStoresTerminalFailure() {
        when(analyses.tryClaim("room-1", NOW, LEASE_UNTIL, 5))
                .thenReturn(Optional.of(new Claim("room-1", LEASE_TOKEN, 5)));
        when(assistant.analyze(any())).thenReturn(Optional.empty());

        service.analyzeOpeningAfterCommit(ticket(), "제목", "본문", null);

        verify(analyses).fail("room-1", LEASE_TOKEN, NOW);
        verify(analyses, never()).retry(any(), any(), any(), any());
    }

    @Test
    void discoveryIdempotentlyRegistersRecentTicketsThenRecoversDueWork() {
        when(sources.findRecentRoomIds(200)).thenReturn(List.of("room-1", "room-2"));
        when(analyses.findRecoverableRoomIds(NOW, 5, 50)).thenReturn(List.of());

        service.discoverMissingWork();

        verify(analyses).enqueue("room-1", NOW);
        verify(analyses).enqueue("room-2", NOW);
    }

    @Test
    void disabledConfigurationDoesNotCaptureDiscoverOrAnalyzeInquiry() {
        SupportAssistantAnalysisService disabled = new SupportAssistantAnalysisService(
                false, assistant, analyses, sources, Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC));

        disabled.analyzeOpeningAfterCommit(ticket(), "제목", "본문", "ko-KR");
        disabled.recoverDueWork();
        disabled.discoverMissingWork();

        verifyNoInteractions(assistant, analyses, sources);
    }

    private SupportAssistantAnalysisService serviceWith(TaskExecutor executor) {
        return new SupportAssistantAnalysisService(
                true, assistant, analyses, sources, executor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SupportTicket ticket() {
        return SupportTicket.opened("room-1", "requester-1", SupportCategory.PRODUCT_FEEDBACK, NOW);
    }

    private static Analysis result() {
        return new Analysis(
                SupportCategory.PRODUCT_FEEDBACK,
                Priority.NORMAL,
                "기능 개선 요청입니다.",
                "의견을 검토하겠습니다.",
                List.of("MANUAL_REVIEW"),
                true,
                false,
                "rules-v1");
    }

    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
