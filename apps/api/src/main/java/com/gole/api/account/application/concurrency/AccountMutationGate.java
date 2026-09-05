package com.gole.api.account.application.concurrency;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * 단일 API 프로세스에서 계정별 변경 요청과 탈퇴 잠금의 순서를 보장한다.
 *
 * <p>일반 변경 요청은 한 permit을, 탈퇴 요청은 모든 permit을 얻는다. 공정 세마포어를 사용하므로
 * 탈퇴 요청이 대기하기 시작한 뒤 들어온 일반 요청은 앞질러 갈 수 없다. 세마포어는 획득 스레드와
 * 반환 스레드가 달라도 안전해 Servlet async 재디스패치에서도 요청 전체 수명을 감쌀 수 있다.
 *
 * <p>이 잠금은 단일 백엔드 인스턴스 전용이다. API를 둘 이상으로 늘리기 전에는 분산 admission
 * gate 또는 데이터베이스 write fence로 교체해야 한다.
 */
@Component
public class AccountMutationGate {

    private static final int SHARED_PERMITS = 1_000_000;

    private final ConcurrentHashMap<String, GateEntry> entries = new ConcurrentHashMap<>();

    public Lease acquireShared(String accountId) {
        return acquire(accountId, 1);
    }

    public Lease acquireExclusive(String accountId) {
        return acquire(accountId, SHARED_PERMITS);
    }

    int trackedAccountCount() {
        return entries.size();
    }

    boolean hasQueuedRequests(String accountId) {
        GateEntry entry = entries.get(accountId);
        return entry != null && entry.admission.hasQueuedThreads();
    }

    private Lease acquire(String rawAccountId, int permits) {
        String accountId = requireAccountId(rawAccountId);
        GateEntry entry = entries.compute(accountId, (ignored, current) -> {
            GateEntry selected = current == null ? new GateEntry() : current;
            selected.references++;
            return selected;
        });
        entry.admission.acquireUninterruptibly(permits);
        return new Lease(() -> release(accountId, entry, permits));
    }

    private void release(String accountId, GateEntry entry, int permits) {
        entry.admission.release(permits);
        entries.compute(accountId, (ignored, current) -> {
            if (current != entry) {
                throw new IllegalStateException("account mutation gate identity changed while leased");
            }
            entry.references--;
            if (entry.references < 0) {
                throw new IllegalStateException("account mutation gate lease released too many times");
            }
            return entry.references == 0 ? null : entry;
        });
    }

    private static String requireAccountId(String accountId) {
        Objects.requireNonNull(accountId, "accountId");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        return accountId;
    }

    private static final class GateEntry {

        private final Semaphore admission = new Semaphore(SHARED_PERMITS, true);
        private int references;
    }

    /** 요청 완료·예외·async 완료 중 어느 경로에서든 한 번만 반환되는 lease. */
    public static final class Lease implements AutoCloseable {

        private final Runnable releaser;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Runnable releaser) {
            this.releaser = releaser;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                releaser.run();
            }
        }
    }
}
