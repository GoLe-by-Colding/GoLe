package com.gole.api.common.config;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/** MongoDB가 커밋 결과를 확정하지 못한 경우, 트랜잭션 본문이 아닌 같은 커밋 명령만 재시도한다. */
class RetryingMongoTransactionManager extends MongoTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(RetryingMongoTransactionManager.class);
    private static final int MAX_COMMIT_ATTEMPTS = 3;
    private static final long COMMIT_RETRY_DELAY_MILLIS = 500L;

    RetryingMongoTransactionManager(MongoDatabaseFactory databaseFactory) {
        super(databaseFactory);
    }

    @Override
    protected void doCommit(MongoTransactionObject transactionObject) throws Exception {
        int attempt = 1;
        while (true) {
            try {
                super.doCommit(transactionObject);
                return;
            } catch (MongoException failure) {
                if (!failure.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                        || attempt >= MAX_COMMIT_ATTEMPTS) {
                    throw failure;
                }
                log.warn(
                        "Retrying ambiguous MongoDB transaction commit result: nextAttempt={}/{}",
                        attempt + 1,
                        MAX_COMMIT_ATTEMPTS);
                pauseBeforeRetry();
                attempt++;
            }
        }
    }

    private static void pauseBeforeRetry() throws InterruptedException {
        try {
            Thread.sleep(COMMIT_RETRY_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }
}
