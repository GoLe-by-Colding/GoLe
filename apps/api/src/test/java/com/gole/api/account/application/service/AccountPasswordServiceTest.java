package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.in.ChangePasswordUseCase.ChangePasswordCommand;
import com.gole.api.account.application.port.in.ConfirmPasswordResetUseCase.ConfirmPasswordResetCommand;
import com.gole.api.account.application.port.in.RequestPasswordResetUseCase.RequestPasswordResetCommand;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort;
import com.gole.api.account.application.port.out.PasswordResetChallengeStorePort.Challenge;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.exception.WeakPasswordException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.Nickname;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.PhoneNumber;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.VerificationCode;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountPasswordServiceTest {

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryResetStore resets = new InMemoryResetStore();
    private final TrackingSessionStore sessions = new TrackingSessionStore();
    private final CapturingSender sender = new CapturingSender();
    private final MutableClock clock = new MutableClock();
    private final PlainHasher hasher = new PlainHasher();
    private AccountPasswordService service;

    @BeforeEach
    void setUp() {
        accounts.clear();
        resets.clear();
        sessions.reset();
        sender.reset();
        clock.reset();
        service = new AccountPasswordService(accounts, hasher, resets, () -> "123456", sender, sessions, clock);
    }

    @Test
    void changePasswordUpdatesHashAndRevokesEverySession() {
        Account account = verified("account-1", "member@gole.test", "old-password");
        sessions.store("token-1", account.getId(), Role.USER, Duration.ofDays(7));
        sessions.store("token-2", account.getId(), Role.USER, Duration.ofDays(7));

        service.change(new ChangePasswordCommand(account.getId(), "old-password", "new-password"));

        assertThat(hasher.matches("new-password", account.getPasswordHash())).isTrue();
        assertThat(sessions.resolve("token-1")).isEmpty();
        assertThat(sessions.resolve("token-2")).isEmpty();
        assertThat(sessions.revokeAllCalls).isEqualTo(1);
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordWithoutRevokingSession() {
        Account account = verified("account-1", "member@gole.test", "old-password");
        sessions.store("token-1", account.getId(), Role.USER, Duration.ofDays(7));

        assertThatThrownBy(() ->
                        service.change(new ChangePasswordCommand(account.getId(), "wrong-password", "new-password")))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("CURRENT_PASSWORD_MISMATCH");

        assertThat(sessions.resolve("token-1")).isPresent();
        assertThat(hasher.matches("old-password", account.getPasswordHash())).isTrue();
    }

    @Test
    void resetRequestDoesNotRevealUnknownOrSuspendedAccount() {
        service.request(new RequestPasswordResetCommand("unknown@gole.test"));
        Account suspended = verified("account-2", "suspended@gole.test", "old-password");
        suspended.suspend("운영 정지");
        service.request(new RequestPasswordResetCommand("suspended@gole.test"));

        assertThat(sender.resetCount).isZero();
        assertThat(resets.values).isEmpty();
    }

    @Test
    void resetRequestStoresHashedCodeAndSilentlyAppliesCooldown() {
        verified("account-1", "member@gole.test", "old-password");

        service.request(new RequestPasswordResetCommand("member@gole.test"));
        service.request(new RequestPasswordResetCommand("member@gole.test"));

        Challenge challenge = resets.find(new Email("member@gole.test")).orElseThrow();
        assertThat(challenge.codeHash()).isEqualTo("plain:123456");
        assertThat(challenge.codeHash()).isNotEqualTo("123456");
        assertThat(sender.resetCount).isEqualTo(1);

        clock.advance(Duration.ofSeconds(61));
        service.request(new RequestPasswordResetCommand("member@gole.test"));
        assertThat(sender.resetCount).isEqualTo(2);
    }

    @Test
    void resetConfirmationConsumesCodeChangesPasswordAndRevokesSessions() {
        Account account = verified("account-1", "member@gole.test", "old-password");
        sessions.store("token-1", account.getId(), Role.USER, Duration.ofDays(7));
        service.request(new RequestPasswordResetCommand("member@gole.test"));

        service.confirm(new ConfirmPasswordResetCommand("member@gole.test", "123456", "new-password"));

        assertThat(hasher.matches("new-password", account.getPasswordHash())).isTrue();
        assertThat(resets.find(account.getEmail())).isEmpty();
        assertThat(sessions.resolve("token-1")).isEmpty();
        assertThatThrownBy(() -> service.confirm(
                        new ConfirmPasswordResetCommand("member@gole.test", "123456", "another-password")))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("PASSWORD_RESET_INVALID");
    }

    @Test
    void resetConfirmationInvalidatesChallengeAfterFiveWrongAttempts() {
        verified("account-1", "member@gole.test", "old-password");
        service.request(new RequestPasswordResetCommand("member@gole.test"));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.confirm(
                            new ConfirmPasswordResetCommand("member@gole.test", "000000", "new-password")))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code")
                    .isEqualTo("PASSWORD_RESET_INVALID");
        }

        assertThat(resets.find(new Email("member@gole.test"))).isEmpty();
    }

    @Test
    void passwordPolicyAlsoAppliesToReset() {
        verified("account-1", "member@gole.test", "old-password");
        service.request(new RequestPasswordResetCommand("member@gole.test"));

        assertThatThrownBy(
                        () -> service.confirm(new ConfirmPasswordResetCommand("member@gole.test", "123456", "short")))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(resets.find(new Email("member@gole.test"))).isPresent();
    }

    private Account verified(String id, String email, String password) {
        Account account = Account.provisioned(id, new Email(email), hasher.hash(password), Role.USER);
        accounts.save(account);
        return account;
    }

    private static final class InMemoryAccountRepository implements AccountRepositoryPort {
        private final Map<String, Account> values = new HashMap<>();

        void clear() {
            values.clear();
        }

        @Override
        public boolean existsByEmail(Email email) {
            return values.containsKey(email.value());
        }

        @Override
        public Account save(Account account) {
            values.put(account.getEmail().value(), account);
            return account;
        }

        @Override
        public Optional<Account> findByEmail(Email email) {
            return Optional.ofNullable(values.get(email.value()));
        }

        @Override
        public Optional<Account> findById(String id) {
            return values.values().stream()
                    .filter(account -> account.getId().equals(id))
                    .findFirst();
        }

        @Override
        public List<Account> findRecent(String emailQuery, int limit) {
            return values.values().stream().limit(limit).toList();
        }

        @Override
        public long countByRole(Role role) {
            return values.values().stream()
                    .filter(account -> account.getRole() == role)
                    .count();
        }

        @Override
        public boolean existsByNickname(Nickname nickname, String excludingAccountId) {
            return false;
        }

        @Override
        public boolean existsByVerifiedPhoneNumber(PhoneNumber phoneNumber, String excludingAccountId) {
            return false;
        }
    }

    private static final class PlainHasher implements PasswordHasherPort {
        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("plain:" + rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash passwordHash) {
            return rawPassword != null && passwordHash.value().equals("plain:" + rawPassword);
        }
    }

    private static final class InMemoryResetStore implements PasswordResetChallengeStorePort {
        private final Map<String, Challenge> values = new HashMap<>();

        void clear() {
            values.clear();
        }

        @Override
        public void store(Email email, Challenge challenge, Duration ttl) {
            values.put(email.value(), challenge);
        }

        @Override
        public Optional<Challenge> find(Email email) {
            return Optional.ofNullable(values.get(email.value()));
        }

        @Override
        public int incrementFailedAttempts(Email email) {
            Challenge current = values.get(email.value());
            if (current == null) {
                return -1;
            }
            int attempts = current.failedAttempts() + 1;
            values.put(
                    email.value(),
                    new Challenge(current.accountId(), current.codeHash(), current.issuedAt(), attempts));
            return attempts;
        }

        @Override
        public boolean consume(Email email, String expectedCodeHash) {
            Challenge current = values.get(email.value());
            if (current == null || !current.codeHash().equals(expectedCodeHash)) {
                return false;
            }
            values.remove(email.value());
            return true;
        }

        @Override
        public void delete(Email email) {
            values.remove(email.value());
        }
    }

    private static final class TrackingSessionStore implements SessionStorePort {
        private final Map<String, SessionPrincipal> values = new HashMap<>();
        private int revokeAllCalls;

        void reset() {
            values.clear();
            revokeAllCalls = 0;
        }

        @Override
        public void store(String token, String accountId, Role role, Duration ttl) {
            values.put(token, new SessionPrincipal(accountId, role));
        }

        @Override
        public Optional<SessionPrincipal> resolve(String token) {
            return Optional.ofNullable(values.get(token));
        }

        @Override
        public void revoke(String token) {
            values.remove(token);
        }

        @Override
        public void revokeAllForAccount(String accountId) {
            revokeAllCalls++;
            values.entrySet().removeIf(entry -> entry.getValue().accountId().equals(accountId));
        }
    }

    private static final class CapturingSender implements VerificationCodeSenderPort {
        private int resetCount;

        void reset() {
            resetCount = 0;
        }

        @Override
        public void send(Email email, VerificationCode code) {}

        @Override
        public void sendPasswordReset(Email email, VerificationCode code) {
            resetCount++;
        }
    }

    private static final class MutableClock extends Clock {
        private static final Instant START = Instant.parse("2026-09-03T00:00:00Z");
        private Instant instant = START;

        void reset() {
            instant = START;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
