package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.GetAccountStandingUseCase.AccountStanding;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountStandingServiceTest {

    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final AccountStandingService service = new AccountStandingService(accounts);

    @Test
    @DisplayName("계정의 관리자·인증·정지 여부만 낸다")
    void standing_reportsFlags() {
        Account admin =
                Account.provisioned("admin-1", new Email("admin@gole.test"), new PasswordHash("hash"), Role.ADMIN);
        when(accounts.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThat(service.standing("admin-1")).contains(new AccountStanding("admin-1", true, true, false));
    }

    @Test
    @DisplayName("정지된 계정은 정지이고 인증 상태가 아니다")
    void standing_suspendedAccountIsNotVerified() {
        Account admin =
                Account.provisioned("admin-1", new Email("admin@gole.test"), new PasswordHash("hash"), Role.ADMIN);
        admin.suspend("보안 검토");
        when(accounts.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThat(service.standing("admin-1")).contains(new AccountStanding("admin-1", true, false, true));
    }

    @Test
    @DisplayName("없는 계정이면 비어 있다")
    void standing_emptyForUnknownAccount() {
        when(accounts.findById("ghost")).thenReturn(Optional.empty());

        assertThat(service.standing("ghost")).isEmpty();
    }
}
