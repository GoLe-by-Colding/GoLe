package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

class AccountAdminTransitionServiceTest {

    @Test
    void ordinaryAdminMutationsCannotUndoOrOverwriteDeletionSuspension() {
        AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
        Account account =
                Account.provisioned("account-1", new Email("member@gole.test"), new PasswordHash("hash"), Role.USER);
        account.suspend(AccountDeletionRequest.suspensionReason("request-1"));
        when(accounts.findById("account-1")).thenReturn(Optional.of(account));
        AccountAdminTransitionService service = new AccountAdminTransitionService(accounts);

        assertBlocked(() -> service.suspend("account-1", "admin-1", "other reason"));
        assertBlocked(() -> service.reinstate("account-1"));
        assertBlocked(() -> service.changeRole("account-1", "admin-1", Role.ADMIN));
    }

    private static void assertBlocked(ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_DELETION_ADMIN_MUTATION_FORBIDDEN");
    }
}
