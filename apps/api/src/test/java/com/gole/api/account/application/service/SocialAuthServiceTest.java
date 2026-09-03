package com.gole.api.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginCommand;
import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginResult;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.OAuthStateStorePort;
import com.gole.api.account.application.port.out.OAuthStateStorePort.OAuthStateContext;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.SocialIdentityProviderPort;
import com.gole.api.account.domain.exception.AccountSuspendedException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.PolicyAcceptance;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 가짜 포트로 소셜 로그인 유스케이스를 검증한다(find-or-create, 세션 발급, 미설정/이메일없음). (S3~S7)
 */
class SocialAuthServiceTest {

    private FakeProvider provider;
    private InMemoryAccounts accounts;
    private InMemorySessions sessions;
    private InMemoryStateStore stateStore;
    private SocialAuthService service;
    private List<PolicyAcceptance> policyAcceptances;

    @BeforeEach
    void setUp() {
        provider = new FakeProvider();
        accounts = new InMemoryAccounts();
        sessions = new InMemorySessions();
        stateStore = new InMemoryStateStore();
        policyAcceptances = new ArrayList<>();
        PasswordHasherPort hasher = new PasswordHasherPort() {
            @Override
            public PasswordHash hash(String raw) {
                return new PasswordHash("hash:" + raw);
            }

            @Override
            public boolean matches(String raw, PasswordHash h) {
                return h.value().equals("hash:" + raw);
            }
        };
        PolicyAcceptanceService policies =
                new PolicyAcceptanceService(policyAcceptances::add, new SignupPolicyProperties(), Clock.systemUTC());
        SocialAccountProvisioner provisioner =
                new SocialAccountProvisioner(accounts, new SequentialIds(), hasher, policies);
        service = new SocialAuthService(
                provider,
                accounts,
                account -> "token-" + account.getId(),
                sessions,
                stateStore,
                event -> {},
                Clock.systemUTC(),
                new SessionPolicyProperties(),
                policies,
                provisioner);
    }

    @Test
    void login_createsAccount_whenEmailUnknown() {
        provider.configured = true;
        provider.profile =
                new SocialIdentityProviderPort.SocialProfile(AuthProvider.GOOGLE, "g-123", "new@example.com", true);
        stateStore.save(
                "s1",
                new OAuthStateContext(AuthProvider.GOOGLE, "https://app/cb", acceptedPolicy()),
                Duration.ofMinutes(10));

        SocialLoginResult result =
                service.login(new SocialLoginCommand(AuthProvider.GOOGLE, "code", "https://app/cb", "s1"));

        assertThat(result.sessionToken()).startsWith("token-");
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(accounts.findByEmail(new Email("new@example.com"))).isPresent();
        assertThat(sessions.store).containsKey(result.sessionToken());
        assertThat(policyAcceptances).singleElement().satisfies(acceptance -> {
            assertThat(acceptance.accountId()).isEqualTo(result.accountId());
            assertThat(acceptance.channel()).isEqualTo(PolicyAcceptance.Channel.SOCIAL_GOOGLE);
        });
    }

    @Test
    void login_reusesAccount_whenEmailExists() {
        provider.configured = true;
        provider.profile =
                new SocialIdentityProviderPort.SocialProfile(AuthProvider.KAKAO, "k-1", "existing@example.com", true);
        Account existing = Account.provisioned(
                "acc-existing", new Email("existing@example.com"), new PasswordHash("hash:x"), Role.USER);
        accounts.save(existing);
        stateStore.save(
                "s2", new OAuthStateContext(AuthProvider.KAKAO, "https://app/cb", null), Duration.ofMinutes(10));

        SocialLoginResult result =
                service.login(new SocialLoginCommand(AuthProvider.KAKAO, "code", "https://app/cb", "s2"));

        assertThat(result.accountId()).isEqualTo("acc-existing");
        assertThat(accounts.saved).isEqualTo(1); // 신규 생성 없음
        assertThat(policyAcceptances).isEmpty();
    }

    @Test
    void login_rejectsSuspendedExistingAccount() {
        provider.configured = true;
        provider.profile = new SocialIdentityProviderPort.SocialProfile(
                AuthProvider.GOOGLE, "g-suspended", "blocked@example.com", true);
        Account blocked = Account.provisioned(
                "acc-blocked", new Email("blocked@example.com"), new PasswordHash("hash:x"), Role.USER);
        blocked.suspend("운영 정책 위반");
        accounts.save(blocked);
        stateStore.save(
                "s-blocked",
                new OAuthStateContext(AuthProvider.GOOGLE, "https://app/cb", null),
                Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.login(
                        new SocialLoginCommand(AuthProvider.GOOGLE, "code", "https://app/cb", "s-blocked")))
                .isInstanceOf(AccountSuspendedException.class);
        assertThat(sessions.store).isEmpty();
    }

    @Test
    void login_rejectsWhenProviderNotConfigured() {
        provider.configured = false;
        assertThatThrownBy(
                        () -> service.login(new SocialLoginCommand(AuthProvider.NAVER, "code", "https://app/cb", "s")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void login_rejectsWhenStateInvalid() {
        provider.configured = true;
        provider.profile =
                new SocialIdentityProviderPort.SocialProfile(AuthProvider.GOOGLE, "g-1", "x@example.com", true);
        // state를 저장하지 않음 → CSRF 검증 실패
        assertThatThrownBy(() ->
                        service.login(new SocialLoginCommand(AuthProvider.GOOGLE, "code", "https://app/cb", "bogus")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void login_rejectsWhenEmailMissing() {
        provider.configured = true;
        provider.profile = new SocialIdentityProviderPort.SocialProfile(AuthProvider.NAVER, "n-1", null, false);
        stateStore.save(
                "s3",
                new OAuthStateContext(AuthProvider.NAVER, "https://app/cb", acceptedPolicy()),
                Duration.ofMinutes(10));
        assertThatThrownBy(
                        () -> service.login(new SocialLoginCommand(AuthProvider.NAVER, "code", "https://app/cb", "s3")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void login_rejectsUnverifiedProviderEmail() {
        provider.configured = true;
        provider.profile =
                new SocialIdentityProviderPort.SocialProfile(AuthProvider.GOOGLE, "g-2", "x@example.com", false);
        stateStore.save(
                "s4",
                new OAuthStateContext(AuthProvider.GOOGLE, "https://app/cb", acceptedPolicy()),
                Duration.ofMinutes(10));

        assertThatThrownBy(() ->
                        service.login(new SocialLoginCommand(AuthProvider.GOOGLE, "code", "https://app/cb", "s4")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void login_rejectsStateWhenRedirectUriChanged() {
        provider.configured = true;
        provider.profile =
                new SocialIdentityProviderPort.SocialProfile(AuthProvider.GOOGLE, "g-3", "x@example.com", true);
        stateStore.save(
                "s5",
                new OAuthStateContext(AuthProvider.GOOGLE, "https://app/cb", acceptedPolicy()),
                Duration.ofMinutes(10));

        assertThatThrownBy(() ->
                        service.login(new SocialLoginCommand(AuthProvider.GOOGLE, "code", "https://evil/cb", "s5")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void enabledProviders_reflectsConfiguration() {
        provider.configured = true;
        assertThat(service.enabledProviders())
                .containsExactly(AuthProvider.GOOGLE, AuthProvider.KAKAO, AuthProvider.NAVER);
        provider.configured = false;
        assertThat(service.enabledProviders()).isEmpty();
    }

    @Test
    void login_rejectsNewSocialAccountWithoutSignupPolicyAcceptance() {
        provider.configured = true;
        provider.profile =
                new SocialIdentityProviderPort.SocialProfile(AuthProvider.KAKAO, "k-new", "new2@example.com", true);
        stateStore.save(
                "s6", new OAuthStateContext(AuthProvider.KAKAO, "https://app/cb", null), Duration.ofMinutes(10));

        assertThatThrownBy(
                        () -> service.login(new SocialLoginCommand(AuthProvider.KAKAO, "code", "https://app/cb", "s6")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("확인");
        assertThat(accounts.findByEmail(new Email("new2@example.com"))).isEmpty();
    }

    // --- 가짜들 ---

    private static final class FakeProvider implements SocialIdentityProviderPort {
        private boolean configured = false;
        private SocialProfile profile;

        @Override
        public boolean isConfigured(AuthProvider p) {
            return configured;
        }

        @Override
        public String authorizeUrl(AuthProvider p, String redirectUri, String state) {
            return "https://auth/" + p.key();
        }

        @Override
        public SocialProfile fetchProfile(AuthProvider p, String code, String redirectUri) {
            return profile;
        }
    }

    private static final class InMemoryAccounts implements AccountRepositoryPort {
        private final Map<String, Account> byEmail = new HashMap<>();
        private int saved = 0;

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
            saved++;
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
    }

    private static final class InMemoryStateStore implements OAuthStateStorePort {
        private final Map<String, OAuthStateContext> store = new HashMap<>();

        @Override
        public void save(String state, OAuthStateContext context, Duration ttl) {
            store.put(state, context);
        }

        @Override
        public Optional<OAuthStateContext> consume(String state) {
            return Optional.ofNullable(store.remove(state));
        }
    }

    private static SignupPolicyAcceptance acceptedPolicy() {
        return new SignupPolicyAcceptance("2026-09-03", "2026-09-03", true, true, true);
    }

    private static final class SequentialIds implements IdentifierGeneratorPort {
        private int n = 0;

        @Override
        public String newAccountId() {
            return "acc-" + (++n);
        }
    }

    private static final class InMemorySessions implements SessionStorePort {
        private final Map<String, SessionPrincipal> store = new HashMap<>();

        @Override
        public void store(String token, String accountId, Role role, Duration ttl) {
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

        @Override
        public void revokeAllForAccount(String accountId) {
            store.entrySet().removeIf(e -> e.getValue().accountId().equals(accountId));
        }
    }
}
