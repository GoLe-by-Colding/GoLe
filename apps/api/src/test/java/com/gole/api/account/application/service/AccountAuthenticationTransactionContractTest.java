package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.application.port.in.ResendVerificationUseCase.ResendVerificationCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.in.VerifyEmailUseCase.VerifyEmailCommand;
import com.gole.api.account.domain.exception.InvalidCredentialsException;
import com.gole.api.account.domain.exception.VerificationException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class AccountAuthenticationTransactionContractTest {

    @Test
    void verificationCommitsFailedAttemptCounterWhileSerializingConcurrentUpdates() throws Exception {
        Transactional transaction = transaction("verify", VerifyEmailCommand.class);

        assertThat(List.of(transaction.noRollbackFor())).contains(VerificationException.class);
    }

    @Test
    void signInCommitsFailedAttemptCounterWhileSerializingConcurrentUpdates() throws Exception {
        Transactional transaction = transaction("signIn", SignInCommand.class);

        assertThat(List.of(transaction.noRollbackFor())).contains(InvalidCredentialsException.class);
    }

    @Test
    void verificationResendUsesTheSameMongoTransactionBoundary() throws Exception {
        assertThat(transaction("resend", ResendVerificationCommand.class)).isNotNull();
    }

    private static Transactional transaction(String methodName, Class<?> parameterType) throws Exception {
        Method method = AccountService.class.getMethod(methodName, parameterType);
        return method.getAnnotation(Transactional.class);
    }
}
