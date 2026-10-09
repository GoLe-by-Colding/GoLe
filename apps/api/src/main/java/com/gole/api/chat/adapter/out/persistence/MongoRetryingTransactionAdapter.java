package com.gole.api.chat.adapter.out.persistence;

import com.gole.api.chat.application.port.out.RetryingTransactionPort;
import com.mongodb.MongoException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MongoDB 독립 트랜잭션 실행기. 일시적 트랜잭션 오류 라벨이나 WriteConflict(112)면 새 트랜잭션으로 최대 3번 다시 시도한다.
 *
 * <p>커밋 결과를 알 수 없는 실패(UnknownTransactionCommitResult)는 다시 실행하지 않는다 — 이미 커밋됐을 수 있어
 * 본문이 두 번 적용될 수 있다.
 */
@Component
public class MongoRetryingTransactionAdapter implements RetryingTransactionPort {

    private static final Logger log = LoggerFactory.getLogger(MongoRetryingTransactionAdapter.class);
    private static final int MAX_TRANSIENT_RETRIES = 3;
    private static final int WRITE_CONFLICT = 112;

    private final TransactionTemplate transaction;

    public MongoRetryingTransactionAdapter(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T inNewTransaction(String operation, String key, Supplier<T> work) {
        int retries = 0;
        while (true) {
            try {
                T result = transaction.execute(ignored -> work.get());
                if (result == null) {
                    throw new IllegalStateException("독립 트랜잭션이 결과 없이 종료되었습니다: " + operation);
                }
                return result;
            } catch (RuntimeException failure) {
                if (!isTransientTransactionFailure(failure) || retries >= MAX_TRANSIENT_RETRIES) {
                    throw failure;
                }
                retries++;
                log.warn(
                        "Retrying after transient MongoDB transaction failure: operation={}, key={}, retry={}/{}",
                        operation,
                        key,
                        retries,
                        MAX_TRANSIENT_RETRIES);
                pauseBeforeRetry(retries);
            }
        }
    }

    private static boolean isTransientTransactionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof MongoException mongoFailure
                    && (mongoFailure.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                            || mongoFailure.getCode() == WRITE_CONFLICT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void pauseBeforeRetry(int retry) {
        try {
            Thread.sleep(10L << (retry - 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("트랜잭션 재시도가 중단되었습니다", interrupted);
        }
    }
}
