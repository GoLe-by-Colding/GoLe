package com.gole.api.chat.application;

import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort;
import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort.Claim;
import com.gole.api.chat.application.port.out.SupportAssistantAnalysisRepositoryPort.StoredAnalysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Analysis;
import com.gole.api.chat.application.port.out.SupportAssistantPort.Request;
import com.gole.api.chat.application.port.out.SupportAssistantWorkSourcePort;
import com.gole.api.chat.config.SupportAssistantAsyncConfiguration;
import com.gole.api.chat.domain.model.SupportTicket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 문의 원문은 기존 메시지를 단일 원본으로 두고, 방 ID 기반 영속 작업으로 관리자 초안을 만든다. */
@Service
public class SupportAssistantAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SupportAssistantAnalysisService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int RECOVERY_BATCH_SIZE = 50;
    private static final int DISCOVERY_BATCH_SIZE = 200;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);

    private final boolean enabled;
    private final SupportAssistantPort assistant;
    private final SupportAssistantAnalysisRepositoryPort analyses;
    private final SupportAssistantWorkSourcePort sources;
    private final TaskExecutor executor;
    private final Clock clock;

    public SupportAssistantAnalysisService(
            @Value("${gole.support-agent.enabled:false}") boolean enabled,
            SupportAssistantPort assistant,
            SupportAssistantAnalysisRepositoryPort analyses,
            SupportAssistantWorkSourcePort sources,
            @Qualifier(SupportAssistantAsyncConfiguration.EXECUTOR_BEAN_NAME) TaskExecutor executor,
            Clock clock) {
        this.enabled = enabled;
        this.assistant = assistant;
        this.analyses = analyses;
        this.sources = sources;
        this.executor = executor;
        this.clock = clock;
    }

    public void analyzeOpeningAfterCommit(SupportTicket ticket, String title, String message, String locale) {
        if (!enabled) {
            return;
        }
        String requestedLocale = locale == null || locale.isBlank() ? "ko-KR" : locale;
        Request opening = new Request(ticket.roomId(), ticket.category(), title, message, requestedLocale);
        Runnable register = () -> registerAndSchedule(ticket.roomId(), opening);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    register.run();
                }
            });
            return;
        }
        register.run();
    }

    public Optional<Analysis> findCompleted(String roomId) {
        return analyses.findCompletedByRoomId(roomId).map(StoredAnalysis::analysis);
    }

    public Map<String, Analysis> findCompleted(List<String> roomIds) {
        return analyses.findCompletedByRoomIds(roomIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        StoredAnalysis::roomId, StoredAnalysis::analysis, firstValueWins()));
    }

    /** 큐 거절·프로세스 재시작·임대 만료 뒤에도 저장된 작업을 다시 실행한다. */
    @Scheduled(
            initialDelayString = "${gole.support-agent.recovery-initial-delay:PT15S}",
            fixedDelayString = "${gole.support-agent.recovery-interval:PT15S}")
    public void recoverDueWork() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now(clock);
        try {
            analyses.findRecoverableRoomIds(now, MAX_ATTEMPTS, RECOVERY_BATCH_SIZE)
                    .forEach(roomId -> schedule(roomId, null));
        } catch (RuntimeException storageFailure) {
            log.warn(
                    "Support assistant recovery scan failed cause={}",
                    storageFailure.getClass().getSimpleName());
        }
    }

    /** 등록 직후 저장 장애까지 복구하도록 최근 문의를 원문 없이 주기적으로 재등록한다. */
    @Scheduled(
            initialDelayString = "${gole.support-agent.discovery-initial-delay:PT30S}",
            fixedDelayString = "${gole.support-agent.discovery-interval:PT5M}")
    public void discoverMissingWork() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now(clock);
        try {
            sources.findRecentRoomIds(DISCOVERY_BATCH_SIZE).forEach(roomId -> analyses.enqueue(roomId, now));
        } catch (RuntimeException storageFailure) {
            log.warn(
                    "Support assistant discovery failed cause={}",
                    storageFailure.getClass().getSimpleName());
        }
        recoverDueWork();
    }

    private void registerAndSchedule(String roomId, Request opening) {
        try {
            analyses.enqueue(roomId, Instant.now(clock));
        } catch (RuntimeException storageFailure) {
            // 최근 문의 discovery가 다시 등록한다. 문의 접수 성공은 분석 저장 장애와 분리한다.
            log.warn(
                    "Support assistant work registration failed cause={}",
                    storageFailure.getClass().getSimpleName());
            return;
        }
        schedule(roomId, opening);
    }

    private void schedule(String roomId, Request opening) {
        try {
            executor.execute(() -> analyzeOnce(roomId, opening));
        } catch (RuntimeException rejected) {
            // PENDING/RETRY 문서가 남으므로 다음 recovery scan이 다시 실행한다.
            log.warn(
                    "Support assistant task could not be scheduled cause={}",
                    rejected.getClass().getSimpleName());
        }
    }

    private void analyzeOnce(String roomId, Request opening) {
        Instant startedAt = Instant.now(clock);
        Claim claim;
        try {
            Optional<Claim> claimed =
                    analyses.tryClaim(roomId, startedAt, startedAt.plus(LEASE_DURATION), MAX_ATTEMPTS);
            if (claimed.isEmpty()) {
                return;
            }
            claim = claimed.orElseThrow();
        } catch (RuntimeException storageFailure) {
            log.warn(
                    "Support assistant claim failed cause={}",
                    storageFailure.getClass().getSimpleName());
            return;
        }

        if (claim.attempt() > MAX_ATTEMPTS) {
            markTerminalFailureWithoutEscaping(claim);
            return;
        }

        try {
            Optional<Request> request = Optional.ofNullable(opening).or(() -> sources.findRequest(roomId));
            if (request.isEmpty()) {
                markRetryWithoutEscaping(claim);
                return;
            }
            Optional<Analysis> result = assistant.analyze(request.orElseThrow());
            if (result.isPresent()) {
                analyses.complete(roomId, claim.leaseToken(), result.orElseThrow(), Instant.now(clock));
            } else {
                markRetryWithoutEscaping(claim);
            }
        } catch (RuntimeException analysisFailure) {
            markRetryWithoutEscaping(claim);
            // 제목·본문·사용자 식별자와 외부 예외 메시지는 어떤 실패 경로에서도 로그에 넣지 않는다.
            log.warn(
                    "Support assistant analysis failed cause={}",
                    analysisFailure.getClass().getSimpleName());
        }
    }

    private void markRetryWithoutEscaping(Claim claim) {
        Instant failedAt = Instant.now(clock);
        try {
            if (claim.attempt() >= MAX_ATTEMPTS) {
                analyses.fail(claim.roomId(), claim.leaseToken(), failedAt);
                return;
            }
            analyses.retry(claim.roomId(), claim.leaseToken(), failedAt, failedAt.plus(retryDelay(claim.attempt())));
        } catch (RuntimeException storageFailure) {
            // 임대가 만료되면 recovery scan이 다시 선점한다.
            log.warn(
                    "Support assistant retry state could not be stored cause={}",
                    storageFailure.getClass().getSimpleName());
        }
    }

    private void markTerminalFailureWithoutEscaping(Claim claim) {
        try {
            analyses.fail(claim.roomId(), claim.leaseToken(), Instant.now(clock));
        } catch (RuntimeException storageFailure) {
            log.warn(
                    "Support assistant terminal state could not be stored cause={}",
                    storageFailure.getClass().getSimpleName());
        }
    }

    private static Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.clamp(attempt - 1, 0, 10);
        Duration delay = BASE_RETRY_DELAY.multipliedBy(multiplier);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private static <V> java.util.function.BinaryOperator<V> firstValueWins() {
        return (first, ignored) -> first;
    }
}
