package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.SocialLoginUseCase;
import com.gole.api.account.application.port.in.SocialLoginUseCase.AuthorizeUrlResult;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.OAuthStateStorePort;
import com.gole.api.account.application.port.out.OAuthStateStorePort.OAuthStateContext;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.SessionTokenPort;
import com.gole.api.account.application.port.out.SocialIdentityProviderPort;
import com.gole.api.account.application.port.out.SocialIdentityProviderPort.SocialProfile;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PolicyAcceptance;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.operations.OperationalEvent;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.gole.api.common.operations.OperationalEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 유스케이스 구현. provider 포트로 프로필을 얻어 이메일 기준 find-or-create 후
 * 기존 로그인과 동일한 불투명 세션 토큰을 발급한다. Account 애그리거트/암호 정책은 수정하지 않는다.
 * (소셜 로그인 스펙 S3~S7)
 */
@Service
public class SocialAuthService implements SocialLoginUseCase {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final SocialIdentityProviderPort identityProvider;
    private final AccountRepositoryPort accountRepository;
    private final SessionTokenPort sessionToken;
    private final SessionStorePort sessionStore;
    private final OAuthStateStorePort stateStore;
    private final OperationalEventPublisher operationalEventPublisher;
    private final Clock clock;
    private final SessionPolicyProperties sessionPolicy;
    private final PolicyAcceptanceService policyAcceptances;
    private final SocialAccountProvisioner socialAccountProvisioner;
    private final OnboardingProperties onboardingProperties;
    private final OAuthRedirectUriPolicy redirectUris;
    private final ThirdPartyProvisionConsentService thirdPartyProvisionConsents;

    public SocialAuthService(
            SocialIdentityProviderPort identityProvider,
            AccountRepositoryPort accountRepository,
            SessionTokenPort sessionToken,
            SessionStorePort sessionStore,
            OAuthStateStorePort stateStore,
            OperationalEventPublisher operationalEventPublisher,
            Clock clock,
            SessionPolicyProperties sessionPolicy,
            PolicyAcceptanceService policyAcceptances,
            SocialAccountProvisioner socialAccountProvisioner,
            OnboardingProperties onboardingProperties,
            OAuthRedirectUriPolicy redirectUris,
            ThirdPartyProvisionConsentService thirdPartyProvisionConsents) {
        this.identityProvider = identityProvider;
        this.accountRepository = accountRepository;
        this.sessionToken = sessionToken;
        this.sessionStore = sessionStore;
        this.stateStore = stateStore;
        this.operationalEventPublisher = operationalEventPublisher;
        this.clock = clock;
        this.sessionPolicy = sessionPolicy;
        this.policyAcceptances = policyAcceptances;
        this.socialAccountProvisioner = socialAccountProvisioner;
        this.onboardingProperties = onboardingProperties;
        this.redirectUris = redirectUris;
        this.thirdPartyProvisionConsents = thirdPartyProvisionConsents;
    }

    @Override
    public List<AuthProvider> enabledProviders() {
        return List.of(AuthProvider.values()).stream()
                .filter(identityProvider::isConfigured)
                .toList();
    }

    @Override
    public AuthorizeUrlResult authorizeUrl(
            AuthProvider provider, String redirectUri, SignupPolicyAcceptance signupPolicyAcceptance, String returnTo) {
        requireConfigured(provider);
        redirectUris.requireAllowed(redirectUri);
        if (signupPolicyAcceptance != null) {
            policyAcceptances.validate(signupPolicyAcceptance);
        }
        // 서버가 state를 발급·저장한다(콜백에서 1회 소비해 CSRF 방지).
        String state = UUID.randomUUID().toString();
        stateStore.save(
                state,
                new OAuthStateContext(provider, redirectUri, signupPolicyAcceptance, OAuthReturnTo.sanitize(returnTo)),
                STATE_TTL);
        return new AuthorizeUrlResult(identityProvider.authorizeUrl(provider, redirectUri, state), state);
    }

    @Override
    public SocialLoginResult login(SocialLoginCommand command) {
        requireConfigured(command.provider());

        // CSRF: 서버가 발급한 state인지 검증(1회 소비).
        OAuthStateContext state = stateStore
                .consume(command.state())
                .orElseThrow(() -> new BadRequestException("OAUTH_STATE_INVALID", "유효하지 않은 로그인 요청입니다"));
        if (state.provider() != command.provider() || !state.redirectUri().equals(command.redirectUri())) {
            throw new BadRequestException("OAUTH_STATE_INVALID", "유효하지 않은 로그인 요청입니다");
        }
        // 과거 버전에서 발급된 state도 배포 뒤 허용목록을 우회하지 못한다. state를 먼저
        // 소비했으므로 잘못된 콜백 역시 재사용할 수 없다.
        redirectUris.requireAllowed(command.redirectUri());

        SocialProfile profile =
                identityProvider.fetchProfile(command.provider(), command.code(), command.redirectUri());

        if (profile.email() == null || profile.email().isBlank()) {
            throw new BadRequestException("OAUTH_EMAIL_UNAVAILABLE", "Provider did not return an email address");
        }
        if (!profile.emailVerified()) {
            throw new BadRequestException("OAUTH_EMAIL_UNVERIFIED", "Provider email address is not verified");
        }

        Email email = new Email(profile.email());
        var existing = accountRepository.findByEmail(email);
        Account account;
        boolean newAccount;
        if (existing.isPresent()) {
            account = existing.get();
            newAccount = false;
        } else {
            try {
                account = socialAccountProvisioner.provision(email, command.provider(), state.signupPolicyAcceptance());
                newAccount = true;
            } catch (EmailAlreadyRegisteredException concurrentSignup) {
                account = accountRepository.findByEmail(email).orElseThrow(() -> concurrentSignup);
                newAccount = false;
            }
        }
        // 이메일 로그인과 동일하게 정지된 계정은 기존 OAuth 연결로도 우회할 수 없다.
        account.ensureNotSuspended();

        // OAuth 동의 화면은 신규가입과 기존 계정 로그인을 구분하기 전에 열린다. 이메일 경쟁
        // 가입에서 패자가 된 경우도 기존 계정 경로가 되므로, 신규 계정 프로비저너가 이미
        // 기록한 경우를 제외하고 선택 동의를 멱등하게 남긴다.
        if (!newAccount && state.signupPolicyAcceptance() != null) {
            thirdPartyProvisionConsents.recordSignupIfAccepted(
                    account.getId(),
                    state.signupPolicyAcceptance(),
                    PolicyAcceptance.Channel.social(command.provider()));
        }

        if (newAccount) {
            operationalEventPublisher.publish(new OperationalEvent(
                    Category.ACCOUNT,
                    Level.SUCCESS,
                    "새 소셜 회원가입",
                    "소셜 로그인으로 신규 계정이 생성되었습니다.",
                    Map.of(
                            "계정 ID",
                            account.getId(),
                            "Provider",
                            command.provider().key()),
                    Instant.now()));
        }

        String token = sessionToken.issue(account);
        Instant issuedAt = Instant.now(clock);
        Duration ttl = sessionPolicy.getIdleTtl().compareTo(sessionPolicy.getAbsoluteTtl()) < 0
                ? sessionPolicy.getIdleTtl()
                : sessionPolicy.getAbsoluteTtl();
        sessionStore.store(token, account.getId(), account.getRole(), issuedAt, issuedAt, ttl);
        // D7: 이번 스코프는 구글만 온보딩 대상이다. 카카오·네이버 신규가입은 기존 동작
        // (즉시 로그인 + newAccount 환영 화면)을 그대로 유지한다.
        boolean onboardingRequired = command.provider() == AuthProvider.GOOGLE
                && account.isOnboardingRequired(onboardingProperties.phoneVerificationRequired());
        return new SocialLoginResult(
                account.getId(), token, account.getRole(), newAccount, onboardingRequired, state.returnTo());
    }

    private void requireConfigured(AuthProvider provider) {
        if (!identityProvider.isConfigured(provider)) {
            throw new BadRequestException("OAUTH_PROVIDER_NOT_CONFIGURED", provider.key() + " 로그인이 아직 설정되지 않았습니다");
        }
    }
}
