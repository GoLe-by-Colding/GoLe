package com.gole.api.common.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;

class RetryingMongoTransactionManagerTest {

    private final MongoDatabaseFactory databaseFactory = mock(MongoDatabaseFactory.class);
    private final TestTransactionManager transactions = new TestTransactionManager(databaseFactory);

    @Test
    void commit_retriesOnlyTheCommitWhenItsResultIsUnknown() throws Exception {
        MongoException unknownResult = unknownCommitResult();
        transactions.failThenCommit(unknownResult);

        transactions.commit();

        transactions.verifyCommitAttempts(2);
    }

    @Test
    void commit_stopsAfterThreeUnknownResultAttempts() throws Exception {
        MongoException unknownResult = unknownCommitResult();
        transactions.alwaysFail(unknownResult);

        assertThatThrownBy(transactions::commit).isSameAs(unknownResult);

        transactions.verifyCommitAttempts(3);
    }

    @Test
    void commit_doesNotRetryDefinitiveFailures() throws Exception {
        MongoException definitiveFailure = new MongoException(11000, "duplicate key");
        transactions.alwaysFail(definitiveFailure);

        assertThatThrownBy(transactions::commit).isSameAs(definitiveFailure);

        transactions.verifyCommitAttempts(1);
    }

    private static MongoException unknownCommitResult() {
        MongoException failure = new MongoException(91, "commit result was lost");
        failure.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        return failure;
    }

    private static final class TestTransactionManager extends RetryingMongoTransactionManager {

        private final MongoTransactionObject transaction = mock(MongoTransactionObject.class);

        private TestTransactionManager(MongoDatabaseFactory databaseFactory) {
            super(databaseFactory);
        }

        private void failThenCommit(MongoException failure) {
            doThrow(failure).doNothing().when(transaction).commitTransaction();
        }

        private void alwaysFail(MongoException failure) {
            doThrow(failure).when(transaction).commitTransaction();
        }

        private void commit() throws Exception {
            doCommit(transaction);
        }

        private void verifyCommitAttempts(int attempts) {
            verify(transaction, times(attempts)).commitTransaction();
        }
    }
}
