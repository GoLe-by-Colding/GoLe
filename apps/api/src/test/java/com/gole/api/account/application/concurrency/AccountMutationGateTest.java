package com.gole.api.account.application.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.application.concurrency.AccountMutationGate.Lease;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class AccountMutationGateTest {

    @Test
    @Timeout(5)
    void exclusiveWaitsForExistingMutationAndLateMutationCannotBarge() throws Exception {
        AccountMutationGate gate = new AccountMutationGate();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Lease existingMutation = gate.acquireShared("account-1");
        try {
            Future<Lease> deletion = workers.submit(() -> gate.acquireExclusive("account-1"));
            awaitQueuedRequest(gate, "account-1");
            Future<Lease> lateMutation = workers.submit(() -> gate.acquireShared("account-1"));

            assertThat(deletion.isDone()).isFalse();
            assertThat(lateMutation.isDone()).isFalse();
            existingMutation.close();

            Lease deletionLease = deletion.get();
            assertThat(lateMutation.isDone()).isFalse();
            deletionLease.close();

            Lease lateLease = lateMutation.get();
            lateLease.close();
            assertThat(gate.trackedAccountCount()).isZero();
        } finally {
            existingMutation.close();
            workers.close();
        }
    }

    @Test
    void leasesCanFinishOnAnotherThreadAndCloseIsIdempotent() throws Exception {
        AccountMutationGate gate = new AccountMutationGate();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Lease shared = gate.acquireShared("account-1");
            worker.submit(shared::close).get();
            shared.close();

            try (Lease ignored = gate.acquireExclusive("account-1")) {
                assertThat(gate.trackedAccountCount()).isEqualTo(1);
            }
            assertThat(gate.trackedAccountCount()).isZero();
        } finally {
            worker.close();
        }
    }

    @Test
    void differentAccountsDoNotBlockEachOther() {
        AccountMutationGate gate = new AccountMutationGate();
        try (Lease first = gate.acquireExclusive("account-1");
                Lease second = gate.acquireExclusive("account-2")) {
            assertThat(gate.trackedAccountCount()).isEqualTo(2);
        }
        assertThat(gate.trackedAccountCount()).isZero();
    }

    private static void awaitQueuedRequest(AccountMutationGate gate, String accountId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!gate.hasQueuedRequests(accountId) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(gate.hasQueuedRequests(accountId)).isTrue();
    }
}
