package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.in.RegisterAccountUseCase.RegisterAccountCommand;
import com.gole.api.account.application.port.in.ResendVerificationUseCase.ResendVerificationCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.in.SignInUseCase.SignInResult;
import com.gole.api.account.application.port.in.VerifyEmailUseCase.VerifyEmailCommand;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.SessionStorePort.SessionPrincipal;
import com.gole.api.account.domain.exception.AccountLockedException;
import com.gole.api.account.domain.exception.AccountNotVerifiedException;
import com.gole.api.account.domain.exception.AccountSuspendedException;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.exception.InvalidCredentialsException;
import com.gole.api.account.domain.exception.PasswordTooLongException;
import com.gole.api.account.domain.exception.VerificationException;
import com.gole.api.account.domain.exception.WeakPasswordException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.PolicyAcceptance;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가짜 outbound port로 프레임워크/DB 없이 계정 유스케이스를 검증한다.
 */
class AccountServiceTest {

    private InMemoryAccountRepository repository;
    private InMemorySessionStore sessionStore;
    private MutableClock clock;
    private AccountService service;
    private List<PolicyAcceptance> policyAcceptances;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        sessionStore = new InMemorySessionStore();
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        policyAcceptances = new ArrayList<>();
        service = new AccountService(
                repository,
                new PlainHasher(),
                (email, code) -> {
                    /* no-op */
                },
                () -> "123456",
                new SequentialIdGenerator(),
                account -> "token-" + account.getId(),
                sessionStore,
                clock,
                new SessionPolicyProperties(),
                policyService(clock));
    }

    // --- admin-console 요구사항 6.4 / 6.5: 정지 계정 차단 ---

    @Test
    void signIn_rejectsSuspendedAccount() {
        service.register(registerCommand("s@b.com", "password1"));
        service.verify(new VerifyEmailCommand("s@b.com", "123456"));
        Account account = repository.findByEmail(new Email("s@b.com")).orElseThrow();
        account.suspend("사기 신고 다발");
        repository.save(account);

        assertThatThrownBy(() -> service.signIn(new SignInCommand("s@b.com", "password1")))
                .isInstanceOf(AccountSuspendedException.class)
                .hasMessageContaining("사기 신고 다발");
    }

    @Test
    void resolve_returnsEmptyForSuspendedAccount() {
        service.register(registerCommand("s2@b.com", "password1"));
        service.verify(new VerifyEmailCommand("s2@b.com", "123456"));
        SignInResult signedIn = service.signIn(new SignInCommand("s2@b.com", "password1"));
        assertThat(service.resolve(signedIn.sessionToken())).isPresent();

        // 세션 토큰은 그대로 살아 있어도(폐기 누락 시나리오) 정지되면 해석에 실패해야 한다.
        Account account = repository.findByEmail(new Email("s2@b.com")).orElseThrow();
        account.suspend("정지");
        repository.save(account);

        assertThat(service.resolve(signedIn.sessionToken())).isEmpty();
    }

    @Test
    void reinstate_allowsSignInAgain() {
        service.register(registerCommand("s3@b.com", "password1"));
        service.verify(new VerifyEmailCommand("s3@b.com", "123456"));
        Account account = repository.findByEmail(new Email("s3@b.com")).orElseThrow();
        account.suspend("정지");
        account.reinstate();
        repository.save(account);

        assertThat(service.signIn(new SignInCommand("s3@b.com", "password1")).sessionToken())
                .isNotBlank();
    }

    @Test
    void register_rejectsShortPassword() {
        assertThatThrownBy(() -> service.register(registerCommand("a@b.com", "short")))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void register_rejectsPasswordBeyondBcryptByteLimit() {
        assertThatThrownBy(() -> service.register(registerCommand("a@b.com", "가".repeat(25))))
                .isInstanceOf(PasswordTooLongException.class);
    }

    @Test
    void signIn_rejectsOversizedPasswordAsInvalidCredentials() {
        service.register(registerCommand("a@b.com", "password1"));

        assertThatThrownBy(() -> service.signIn(new SignInCommand("a@b.com", "x".repeat(73))))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void register_rejectsDuplicateEmail() {
        service.register(registerCommand("a@b.com", "password1"));
        assertThatThrownBy(() -> service.register(registerCommand("A@b.com", "password2")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void register_recordsCurrentPolicyAcceptance() {
        String accountId = service.register(registerCommand("policy@b.com", "password1"));

        assertThat(policyAcceptances).singleElement().satisfies(acceptance -> {
            assertThat(acceptance.accountId()).isEqualTo(accountId);
            assertThat(acceptance.termsVersion()).isEqualTo("2026-09-03");
            assertThat(acceptance.privacyVersion()).isEqualTo("2026-09-03");
            assertThat(acceptance.minimumAgeConfirmed()).isTrue();
            assertThat(acceptance.channel()).isEqualTo(PolicyAcceptance.Channel.EMAIL);
        });
    }

    @Test
    void register_rejectsStaleOrIncompletePolicyBeforeCreatingAccount() {
        var stale = new SignupPolicyAcceptance("old", "2026-09-03", true, true, true);

        assertThatThrownBy(() -> service.register(new RegisterAccountCommand("stale@b.com", "password1", stale)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("정책이 변경");
        assertThat(repository.findByEmail(new Email("stale@b.com"))).isEmpty();
        assertThat(policyAcceptances).isEmpty();
    }

    @Test
    void verify_succeeds_withValidCodeInTime() {
        service.register(registerCommand("a@b.com", "password1"));
        service.verify(new VerifyEmailCommand("a@b.com", "123456"));
        assertThat(repository.findByEmail(new Email("a@b.com")).orElseThrow().isVerified())
                .isTrue();
    }

    @Test
    void verify_fails_whenCodeExpired() {
        service.register(registerCommand("a@b.com", "password1"));
        clock.advance(Duration.ofMinutes(11)); // 10분 초과
        assertThatThrownBy(() -> service.verify(new VerifyEmailCommand("a@b.com", "123456")))
                .isInstanceOf(VerificationException.class);
    }

    @Test
    void verify_invalidatesCodeAfterFiveMismatches() {
        service.register(registerCommand("a@b.com", "password1"));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> service.verify(new VerifyEmailCommand("a@b.com", "000000")))
                    .isInstanceOf(VerificationException.class);
        }
        assertThatThrownBy(() -> service.verify(new VerifyEmailCommand("a@b.com", "000000")))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("초과");

        Account account = repository.findByEmail(new Email("a@b.com")).orElseThrow();
        assertThat(account.getVerificationCode()).isNotNull();
        assertThat(account.getVerificationFailedAttempts()).isEqualTo(5);
        assertThatThrownBy(() -> service.verify(new VerifyEmailCommand("a@b.com", "123456")))
                .isInstanceOf(VerificationException.class);
    }

    @Test
    void signIn_succeeds_withCorrectPassword() {
        service.register(registerCommand("a@b.com", "password1"));
        service.verify(new VerifyEmailCommand("a@b.com", "123456"));
        SignInResult result = service.signIn(new SignInCommand("a@b.com", "password1"));
        assertThat(result.sessionToken()).startsWith("token-");
    }

    @Test
    void signIn_rejectsUnverifiedAccount() {
        service.register(registerCommand("pending@b.com", "password1"));

        assertThatThrownBy(() -> service.signIn(new SignInCommand("pending@b.com", "password1")))
                .isInstanceOf(AccountNotVerifiedException.class);
    }

    @Test
    void signIn_doesNotRevealUnverifiedAccountWhenPasswordIsWrong() {
        service.register(registerCommand("pending@b.com", "password1"));

        assertThatThrownBy(() -> service.signIn(new SignInCommand("pending@b.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void resend_reissuesCodeAfterCooldown() {
        service.register(registerCommand("pending@b.com", "password1"));
        clock.advance(Duration.ofSeconds(61));

        service.resend(new ResendVerificationCommand("pending@b.com"));

        Account account = repository.findByEmail(new Email("pending@b.com")).orElseThrow();
        assertThat(account.getVerificationCode()).isNotNull();
        assertThat(account.getVerificationCode().issuedAt()).isEqualTo(clock.instant());
    }

    @Test
    void resend_rejectsRapidRepeat() {
        service.register(registerCommand("pending@b.com", "password1"));

        assertThatThrownBy(() -> service.resend(new ResendVerificationCommand("pending@b.com")))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("60초");
    }

    @Test
    void resend_doesNotRevealUnknownEmail() {
        service.resend(new ResendVerificationCommand("unknown@b.com"));

        assertThat(repository.findByEmail(new Email("unknown@b.com"))).isEmpty();
    }

    @Test
    void logout_revokesSession() {
        service.register(registerCommand("a@b.com", "password1"));
        service.verify(new VerifyEmailCommand("a@b.com", "123456"));
        SignInResult result = service.signIn(new SignInCommand("a@b.com", "password1"));
        assertThat(service.resolve(result.sessionToken())).isPresent();

        service.logout(result.sessionToken());

        assertThat(service.resolve(result.sessionToken())).isEmpty();
    }

    @Test
    void signIn_storesVersionedSessionWithIdleTtl() {
        service.register(registerCommand("ttl@b.com", "password1"));
        service.verify(new VerifyEmailCommand("ttl@b.com", "123456"));

        SignInResult signedIn = service.signIn(new SignInCommand("ttl@b.com", "password1"));

        SessionPrincipal stored = sessionStore.resolve(signedIn.sessionToken()).orElseThrow();
        assertThat(stored.issuedAt()).isEqualTo(clock.instant());
        assertThat(stored.rotatedAt()).isEqualTo(clock.instant());
        assertThat(sessionStore.lastStoredTtl).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void resolve_extendsIdleTtlButRejectsAbsoluteExpiry() {
        service.register(registerCommand("absolute@b.com", "password1"));
        service.verify(new VerifyEmailCommand("absolute@b.com", "123456"));
        SignInResult signedIn = service.signIn(new SignInCommand("absolute@b.com", "password1"));

        clock.advance(Duration.ofHours(23));
        assertThat(service.resolve(signedIn.sessionToken())).isPresent();
        assertThat(sessionStore.touchCount).isEqualTo(1);
        assertThat(sessionStore.lastTouchedTtl).isEqualTo(Duration.ofDays(1));

        clock.advance(Duration.ofDays(7));
        assertThat(service.resolve(signedIn.sessionToken())).isEmpty();
        assertThat(sessionStore.resolve(signedIn.sessionToken())).isEmpty();
    }

    @Test
    void refresh_rotatesAfterThresholdWithoutResettingAbsoluteLifetime() {
        AtomicInteger sequence = new AtomicInteger();
        InMemorySessionStore rotatingStore = new InMemorySessionStore();
        AccountService rotatingService = new AccountService(
                repository,
                new PlainHasher(),
                (email, code) -> {},
                () -> "123456",
                new SequentialIdGenerator(),
                account -> "rotating-token-" + sequence.incrementAndGet(),
                rotatingStore,
                clock,
                new SessionPolicyProperties(),
                policyService(clock));
        rotatingService.register(registerCommand("rotate@b.com", "password1"));
        rotatingService.verify(new VerifyEmailCommand("rotate@b.com", "123456"));
        SignInResult signedIn = rotatingService.signIn(new SignInCommand("rotate@b.com", "password1"));
        Instant issuedAt =
                rotatingStore.resolve(signedIn.sessionToken()).orElseThrow().issuedAt();

        clock.advance(Duration.ofHours(11));
        var unchanged = rotatingService.refresh(signedIn.sessionToken()).orElseThrow();
        assertThat(unchanged.rotated()).isFalse();
        assertThat(unchanged.sessionToken()).isEqualTo(signedIn.sessionToken());

        clock.advance(Duration.ofHours(2));
        var rotated = rotatingService.refresh(signedIn.sessionToken()).orElseThrow();
        assertThat(rotated.rotated()).isTrue();
        assertThat(rotated.sessionToken()).isNotEqualTo(signedIn.sessionToken());
        assertThat(rotatingStore.resolve(signedIn.sessionToken())).isEmpty();
        assertThat(rotatingStore.resolve(rotated.sessionToken()).orElseThrow().issuedAt())
                .isEqualTo(issuedAt);

        clock.advance(Duration.ofDays(7));
        assertThat(rotatingService.refresh(rotated.sessionToken())).isEmpty();
    }

    @Test
    void signIn_locksAccount_afterFiveFailures() {
        service.register(registerCommand("a@b.com", "password1"));
        service.verify(new VerifyEmailCommand("a@b.com", "123456"));
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
                (email, code) -> {
                    /* no-op */
                },
                () -> "123456",
                new SequentialIdGenerator(),
                account -> "token-" + account.getId(),
                new InMemorySessionStore(),
                clock,
                new SessionPolicyProperties(),
                policyService(clock));
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

    private static RegisterAccountCommand registerCommand(String email, String password) {
        return new RegisterAccountCommand(email, password, acceptedPolicy());
    }

    private static SignupPolicyAcceptance acceptedPolicy() {
        return new SignupPolicyAcceptance("2026-09-03", "2026-09-03", true, true, true);
    }

    private PolicyAcceptanceService policyService(Clock policyClock) {
        return new PolicyAcceptanceService(policyAcceptances::add, new SignupPolicyProperties(), policyClock);
    }

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
        public Optional<Account> findById(String id) {
            return byEmail.values().stream().filter(a -> a.getId().equals(id)).findFirst();
        }

        @Override
        public Account save(Account account) {
            byEmail.put(account.getEmail().value(), account);
            return account;
        }

        @Override
        public java.util.List<Account> findRecent(String emailQuery, int limit) {
            return byEmail.values().stream()
                    .filter(a -> emailQuery == null
                            || emailQuery.isBlank()
                            || a.getEmail().value().contains(emailQuery))
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countByRole(com.gole.api.account.domain.model.Role role) {
            return byEmail.values().stream().filter(a -> a.getRole() == role).count();
        }

        @Override
        public boolean existsByNickname(
                com.gole.api.account.domain.model.Nickname nickname, String excludingAccountId) {
            return false; // 온보딩은 이 테스트의 관심사가 아니다.
        }

        @Override
        public boolean existsByVerifiedPhoneNumber(
                com.gole.api.account.domain.model.PhoneNumber phoneNumber, String excludingAccountId) {
            return false;
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
            return hash.value().equals("bcrypt:" + rawPassword) || hash.value().equals("legacy:" + rawPassword);
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
        private Duration lastStoredTtl;
        private Duration lastTouchedTtl;
        private int touchCount;

        @Override
        public void store(String token, String accountId, com.gole.api.account.domain.model.Role role, Duration ttl) {
            store.put(token, new SessionPrincipal(accountId, role));
            lastStoredTtl = ttl;
        }

        @Override
        public void store(
                String token,
                String accountId,
                com.gole.api.account.domain.model.Role role,
                Instant issuedAt,
                Instant rotatedAt,
                Duration ttl) {
            store.put(token, new SessionPrincipal(accountId, role, issuedAt, rotatedAt));
            lastStoredTtl = ttl;
        }

        @Override
        public Optional<SessionPrincipal> resolve(String token) {
            return Optional.ofNullable(store.get(token));
        }

        @Override
        public void touch(String token, String accountId, Duration ttl) {
            if (store.containsKey(token)) {
                lastTouchedTtl = ttl;
                touchCount++;
            }
        }

        @Override
        public void revoke(String token) {
            store.remove(token);
        }

        @Override
        public void revokeAllForAccount(String accountId) {
            store.entrySet().removeIf(e -> e.getValue().accountId().equals(accountId));
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
