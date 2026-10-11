package com.gole.api.chat.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class MongoRetryingTransactionAdapterTest {

    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final MongoRetryingTransactionAdapter adapter;
    private final AtomicInteger attempts = new AtomicInteger();

    MongoRetryingTransactionAdapterTest() {
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        adapter = new MongoRetryingTransactionAdapter(transactions);
    }

    @Test
    @DisplayName("WriteConflict(112)는 새 트랜잭션으로 다시 시도해 성공하면 결과를 돌려준다")
    void retriesWriteConflictAndThenSucceeds() {
        MongoException writeConflict = new MongoException(112, "WriteConflict");

        String result = adapter.inNewTransaction("op", "key", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw writeConflict;
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("다른 예외로 감싼 일시적 트랜잭션 라벨도 다시 시도한다")
    void retriesWrappedTransientTransactionLabel() {
        MongoException transientFailure = new MongoException(251, "transaction aborted");
        transientFailure.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        RuntimeException translated = new IllegalStateException("translated", transientFailure);

        adapter.inNewTransaction("op", "key", () -> {
            if (attempts.incrementAndGet() == 1) {
                throw translated;
            }
            return "ok";
        });

        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("일시적 실패가 계속되면 3번 다시 시도한 뒤 원래 예외를 던진다")
    void stopsAfterThreeTransientRetries() {
        MongoException writeConflict = new MongoException(112, "WriteConflict");

        assertThatThrownBy(() -> adapter.inNewTransaction("op", "key", () -> {
                    attempts.incrementAndGet();
                    throw writeConflict;
                }))
                .isSameAs(writeConflict);
        assertThat(attempts).hasValue(4);
    }

    @Test
    @DisplayName("일시적이 아닌 Mongo 실패는 다시 시도하지 않는다")
    void doesNotRetryNonTransientMongoFailure() {
        MongoException duplicateKey = new MongoException(11000, "duplicate key");

        assertThatThrownBy(() -> adapter.inNewTransaction("op", "key", () -> {
                    attempts.incrementAndGet();
                    throw duplicateKey;
                }))
                .isSameAs(duplicateKey);
        assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("커밋 결과를 알 수 없으면 본문을 다시 실행하지 않는다")
    void doesNotRerunTheBodyWhenCommitResultIsUnknown() {
        MongoException unknownResult = new MongoException(91, "commit result was lost");
        unknownResult.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        TransactionSystemException commitFailure =
                new TransactionSystemException("could not confirm commit", unknownResult);
        doThrow(commitFailure).when(transactions).commit(any());

        assertThatThrownBy(() -> adapter.inNewTransaction("op", "key", () -> {
                    attempts.incrementAndGet();
                    return "applied";
                }))
                .isSameAs(commitFailure);
        assertThat(attempts).hasValue(1);
    }
}
