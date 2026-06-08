package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.in.RegisterAccountUseCase.RegisterAccountCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInResult;
import com.gole.api.account.application.port.in.VerifyEmailUseCase.VerifyEmailCommand;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.SessionStorePort.SessionPrincipal;
import com.gole.api.account.application.port.out.SessionTokenPort;
import com.gole.api.account.application.port.out.VerificationCodeGeneratorPort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.exception.AccountLockedException;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.exception.InvalidCredentialsException;
import com.gole.api.account.domain.exception.VerificationException;
import com.gole.api.account.domain.exception.WeakPasswordException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가짜 outbound port로 프레임워크/DB 없이 계정 유스케이스를 검증한다.
 */
class AccountServiceTest {

    private InMemoryAccountRepository repository;
    private MutableClock clock;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new AccountService(
                repository,
                new PlainHasher(),
                (email, code) -> { /* no-op */ },
                () -> "123456",
                new SequentialIdGenerator(),
                account -> "token-" + account.getId(),
                new InMemorySessionStore(),
                clock);
    }

    @Test
    void register_rejectsShortPassword() {
        assertThatThrownBy(() ->
                service.register(new RegisterAccountCommand("a@b.com", "short")))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void register_rejectsDuplicateEmail() {
        service.register(new RegisterAccountCommand("a@b.com", "password1"));
        assertThatThrownBy(() ->
                service.register(new RegisterAccountCommand("A@b.com", "password2")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void verify_succeeds_withValidCodeInTime() {
        service.register(new RegisterAccountCommand("a@b.com", "password1"));
        service.verify(new VerifyEmailCommand("a@b.com", "123456"));
        assertThat(repository.findByEmail(new Email("a@b.com")).orElseThrow().isVerified()).isTrue();
    }

    @Test
    void verify_fails_whenCodeExpired() {
        service.register(new RegisterAccountCommand("a@b.com", "password1"));
        clock.advance(Duration.ofMinutes(11)); // 10분 초과
        assertThatThrownBy(() -> service.verify(new VerifyEmailCommand("a@b.com", "123456")))
                .isInstanceOf(VerificationException.class);
    }

    @Test
    void signIn_succeeds_withCorrectPassword() {
        service.register(new RegisterAccountCommand("a@b.com", "password1"));
        SignInResult result = service.signIn(new SignInCommand("a@b.com", "password1"));
        assertThat(result.sessionToken()).startsWith("token-");
    }

    @Test
    void signIn_locksAccount_afterFiveFailures() {
        service.register(new RegisterAccountCommand("a@b.com", "password1"));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.signIn(new SignInCommand("a@b.com", "wrong")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        // 6번째는 잠금으로 인해 자격 검증 전에 차단
        assertThatThrownBy(() -> service.signIn(new SignInCommand("a@b.com", "password1")))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void signIn_upgradesLegacyHash_onSuccess() {
        // 요구사항 1.12: 레거시 해시로 저장된 기존 계정을 시드한다.
        AccountService upgradingService = new AccountService(
                repository,
                new UpgradingHasher(),
                (email, code) -> { /* no-op */ },
                () -> "123456",
                new SequentialIdGenerator(),
                account -> "token-" + account.getId(),
                new InMemorySessionStore(),
                clock);
        repository.save(Account.provisioned(
                "acc-legacy",
                new Email("legacy@b.com"),
                new PasswordHash("legacy:password1"),
                com.gole.api.account.domain.model.Role.USER));

        upgradingService.signIn(new SignInCommand("legacy@b.com", "password1"));

        // 로그인 성공 후 저장된 해시가 BCrypt(여기선 "bcrypt:") 포맷으로 승격되어야 한다.
        PasswordHash stored =
                repository.findByEmail(new Email("legacy@b.com")).orElseThrow().getPasswordHash();
        assertThat(stored.value()).isEqualTo("bcrypt:password1");
    }

    // --- 가짜 구현들 ---

    private static final class InMemoryAccountRepository implements AccountRepositoryPort {
        private final Map<String, Account> byEmail = new HashMap<>();

        @Override
        public boolean existsByEmail(Email email) {
            return byEmail.containsKey(email.value());
        }

        @Override
        public Optional<Account> findByEmail(Email email) {
            return Optional.ofNullable(byEmail.get(email.value()));
        }

        @Override
        public Account save(Account account) {
            byEmail.put(account.getEmail().value(), account);
            return account;
        }
    }

    private static final class PlainHasher implements PasswordHasherPort {
        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("plain:" + rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash hash) {
            return hash.value().equals("plain:" + rawPassword);
        }
    }

    /** 레거시("legacy:") 해시를 검증하되, 성공 시 BCrypt("bcrypt:")로 승격을 요구하는 페이크. */
    private static final class UpgradingHasher implements PasswordHasherPort {
        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("bcrypt:" + rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash hash) {
            return hash.value().equals("bcrypt:" + rawPassword)
                    || hash.value().equals("legacy:" + rawPassword);
        }

        @Override
        public boolean needsRehash(PasswordHash hash) {
            return hash.value().startsWith("legacy:");
        }
    }

    private static final class SequentialIdGenerator implements IdentifierGeneratorPort {
        private int counter = 0;

        @Override
        public String newAccountId() {
            return "acc-" + (++counter);
        }
    }

    private static final class InMemorySessionStore implements SessionStorePort {
        private final Map<String, SessionPrincipal> store = new HashMap<>();

        @Override
        public void store(String token, String accountId, com.gole.api.account.domain.model.Role role, Duration ttl) {
            store.put(token, new SessionPrincipal(accountId, role));
        }

        @Override
        public Optional<SessionPrincipal> resolve(String token) {
            return Optional.ofNullable(store.get(token));
        }

        @Override
        public void revoke(String token) {
            store.remove(token);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
