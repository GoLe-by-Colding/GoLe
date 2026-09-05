package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.RequestAccountDeletionUseCase.Command;
import com.gole.api.account.application.port.out.AccountDeletionRepositoryPort;
import com.gole.api.account.application.port.out.AccountDeletionVerificationStorePort;
import com.gole.api.account.application.port.out.AccountDeletionVerificationStorePort.Challenge;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort;
import com.gole.api.account.application.port.out.PhoneVerificationStorePort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.config.EmailAuthenticationAvailability;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountDeletionBlocker;
import com.gole.api.account.domain.model.AccountDeletionRequest;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.exception.ServiceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private final AccountRepositoryPort accounts = mock(AccountRepositoryPort.class);
    private final AccountDeletionRepositoryPort requests = mock(AccountDeletionRepositoryPort.class);
    private final AccountDeletionVerificationStorePort verifications = mock(AccountDeletionVerificationStorePort.class);
    private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    private final VerificationCodeSenderPort sender = mock(VerificationCodeSenderPort.class);
    private final SessionStorePort sessions = mock(SessionStorePort.class);
    private final PasswordResetChallengeStorePort passwordResets = mock(PasswordResetChallengeStorePort.class);
    private final PhoneVerificationStorePort phoneVerifications = mock(PhoneVerificationStorePort.class);
    private final AtomicReference<AccountDeletionRequest> storedRequest = new AtomicReference<>();
    private Account account;
    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        account = Account.provisioned(
                "account-1", new Email("member@gole.test"), new PasswordHash("password-hash"), Role.USER);
        when(accounts.findById("account-1")).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hasher.matches("123456", new PasswordHash("code-hash"))).thenReturn(true);
        when(verifications.find("account-1")).thenReturn(Optional.of(new Challenge("code-hash", NOW, 0)));
        when(verifications.consume("account-1", "code-hash")).thenReturn(true);
        when(requests.findActiveByAccountId("account-1"))
                .thenAnswer(ignored -> Optional.ofNullable(storedRequest.get()));
        when(requests.evaluateBlockers("account-1", false)).thenReturn(List.of(AccountDeletionBlocker.ACTIVE_ORDER));
        when(requests.save(any())).thenAnswer(invocation -> {
            AccountDeletionRequest request = invocation.getArgument(0);
            storedRequest.set(request);
            return request;
        });
        service = new AccountDeletionService(
                accounts,
                requests,
                verifications,
                hasher,
                () -> "123456",
                sender,
                sessions,
                passwordResets,
                phoneVerifications,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new EmailAuthenticationAvailability("test", false));
    }

    @Test
    void verifiedRequestImmediatelySuspendsAccountRevokesSessionsAndReturnsStructuredBlockers() {
        var result = service.request(command("member@gole.test", "회원 탈퇴", "123456", IDEMPOTENCY_KEY));

        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getSuspendedReason()).isEqualTo(AccountDeletionRequest.suspensionReason(result.requestId()));
        assertThat(result.blockers()).containsExactly(AccountDeletionBlocker.ACTIVE_ORDER);
        assertThat(result.status().name()).isEqualTo("BLOCKED");
        verify(verifications).consume("account-1", "code-hash");
        verify(sessions).revokeAllForAccount("account-1");
    }

    @Test
    void publicEnvironmentWithoutMailRejectsDeletionBeforeReadingOrStoringAccountData() {
        AccountDeletionService unavailable = new AccountDeletionService(
                accounts,
                requests,
                verifications,
                hasher,
                () -> "123456",
                sender,
                sessions,
                passwordResets,
                phoneVerifications,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new EmailAuthenticationAvailability("production", false));

        assertThatThrownBy(() -> unavailable.issueVerification("account-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", EmailAuthenticationAvailability.UNAVAILABLE_CODE);
        assertThatThrownBy(() -> unavailable.request(command("member@gole.test", "회원 탈퇴", "123456", IDEMPOTENCY_KEY)))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(accounts, never()).findById("account-1");
        verify(verifications, never()).store(eq("account-1"), any(), any());
    }

    @Test
    void exactReplayReturnsOriginalRequestWithoutConsumingCodeOrRevokingTwice() {
        var first = service.request(command("member@gole.test", "회원 탈퇴", "123456", IDEMPOTENCY_KEY));
        var replay = service.request(command("member@gole.test", "회원 탈퇴", "123456", IDEMPOTENCY_KEY));

        assertThat(replay.requestId()).isEqualTo(first.requestId());
        verify(verifications, times(1)).consume("account-1", "code-hash");
        verify(sessions, times(1)).revokeAllForAccount("account-1");
    }

    @Test
    void sameKeyWithDifferentPayloadConflictsAndDoesNotRunASecondMutation() {
        service.request(command("member@gole.test", "회원 탈퇴", "123456", IDEMPOTENCY_KEY));

        assertThatThrownBy(() -> service.request(command("member@gole.test", "회원 탈퇴", "654321", IDEMPOTENCY_KEY)))
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_DELETION_ALREADY_REQUESTED");
        verify(sessions, times(1)).revokeAllForAccount("account-1");
    }

    @Test
    void emailAndPhraseMustBothMatchBeforeOtpIsTouched() {
        assertThatThrownBy(() -> service.request(command("other@gole.test", "회원 탈퇴", "123456", IDEMPOTENCY_KEY)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_DELETION_EMAIL_MISMATCH");

        assertThatThrownBy(() -> service.request(command("member@gole.test", "탈퇴", "123456", IDEMPOTENCY_KEY)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_DELETION_CONFIRMATION_MISMATCH");
        verify(verifications, never()).consume(eq("account-1"), any());
        verify(sessions, never()).revokeAllForAccount(any());
    }

    private static Command command(String email, String phrase, String code, String key) {
        return new Command("account-1", email, phrase, code, key);
    }
}
