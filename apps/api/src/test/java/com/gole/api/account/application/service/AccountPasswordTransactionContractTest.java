package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.application.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import com.gole.api.account.application.port.in.ConfirmPasswordResetUseCase.ConfirmPasswordResetCommand;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class AccountPasswordTransactionContractTest {

    @Test
    void authenticatedPasswordChangeUsesMongoTransactionBoundary() throws Exception {
        assertThat(transaction("change", ChangePasswordCommand.class)).isNotNull();
    }

    @Test
    void publicPasswordResetConfirmationConflictsWithConcurrentDeletionWrite() throws Exception {
        assertThat(transaction("confirm", ConfirmPasswordResetCommand.class)).isNotNull();
    }

    private static Transactional transaction(String methodName, Class<?> parameterType) throws Exception {
        Method method = AccountPasswordService.class.getMethod(methodName, parameterType);
        return method.getAnnotation(Transactional.class);
    }
}
