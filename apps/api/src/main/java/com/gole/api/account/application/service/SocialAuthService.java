package com.gole.api.account.application.service;

import com.gole.api.account.application.port.in.SocialLoginUseCase;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.application.port.out.IdentifierGeneratorPort;
import com.gole.api.account.application.port.out.OAuthStateStorePort;
import com.gole.api.account.application.port.out.PasswordHasherPort;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.SessionTokenPort;
import com.gole.api.account.application.port.out.SocialIdentityProviderPort;
import com.gole.api.account.application.port.out.SocialIdentityProviderPort.SocialProfile;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.BadRequestException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 유스케이스 구현. provider 포트로 프로필을 얻어 이메일 기준 find-or-create 후
 * 기존 로그인과 동일한 불투명 세션 토큰을 발급한다. Account 애그리거트/암호 정책은 수정하지 않는다.
 * (소셜 로그인 스펙 S3~S7)
 */
@Service
public class SocialAuthService implements SocialLoginUseCase {

    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final SocialIdentityProviderPort identityProvider;
    private final AccountRepositoryPort accountRepository;
    private final IdentifierGeneratorPort identifierGenerator;
    private final PasswordHasherPort passwordHasher;
    private final SessionTokenPort sessionToken;
    private final SessionStorePort sessionStore;
    private final OAuthStateStorePort stateStore;

    public SocialAuthService(
            SocialIdentityProviderPort identityProvider,
            AccountRepositoryPort accountRepository,
            IdentifierGeneratorPort identifierGenerator,
            PasswordHasherPort passwordHasher,
            SessionTokenPort sessionToken,
            SessionStorePort sessionStore,
            OAuthStateStorePort stateStore) {
        this.identityProvider = identityProvider;
        this.accountRepository = accountRepository;
        this.identifierGenerator = identifierGenerator;
        this.passwordHasher = passwordHasher;
        this.sessionToken = sessionToken;
        this.sessionStore = sessionStore;
        this.stateStore = stateStore;
    }

    @Override
    public List<AuthProvider> enabledProviders() {
        return List.of(AuthProvider.values()).stream()
                .filter(identityProvider::isConfigured)
                .toList();
    }

    @Override
    public String authorizeUrl(AuthProvider provider, String redirectUri) {
        requireConfigured(provider);
        // 서버가 state를 발급·저장한다(콜백에서 1회 소비해 CSRF 방지).
        String state = UUID.randomUUID().toString();
        stateStore.save(state, provider, STATE_TTL);
        return identityProvider.authorizeUrl(provider, redirectUri, state);
    }

    @Override
    public SocialLoginResult login(SocialLoginCommand command) {
        requireConfigured(command.provider());

        // CSRF: 서버가 발급한 state인지 검증(1회 소비).
        if (!stateStore.consume(command.state(), command.provider())) {
            throw new BadRequestException("OAUTH_STATE_INVALID", "유효하지 않은 로그인 요청입니다");
        }

        SocialProfile profile = identityProvider.fetchProfile(
                command.provider(), command.code(), command.redirectUri());

        if (profile.email() == null || profile.email().isBlank()) {
            throw new BadRequestException(
                    "OAUTH_EMAIL_UNAVAILABLE", "Provider did not return an email address");
        }

        Email email = new Email(profile.email());
        var existing = accountRepository.findByEmail(email);
        Account account = existing.orElseGet(() -> createSocialAccount(email));
        boolean newAccount = existing.isEmpty();

        String token = sessionToken.issue(account);
        sessionStore.store(token, account.getId(), account.getRole(), SESSION_TTL);
        return new SocialLoginResult(account.getId(), token, account.getRole(), newAccount);
    }

    /** 소셜 신규 계정: 인증완료(VERIFIED)·USER·임의 비밀번호(소셜 전용, 암호 로그인 불가). */
    private Account createSocialAccount(Email email) {
        String id = identifierGenerator.newAccountId();
        // 임의의 추측 불가 비밀번호 → 소셜 사용자는 패스워드 로그인을 사용하지 않는다.
        var hash = passwordHasher.hash(UUID.randomUUID().toString());
        Account account = Account.provisioned(id, email, hash, Role.USER);
        return accountRepository.save(account);
    }

    private void requireConfigured(AuthProvider provider) {
        if (!identityProvider.isConfigured(provider)) {
            throw new BadRequestException(
                    "OAUTH_PROVIDER_NOT_CONFIGURED",
                    provider.key() + " 로그인이 아직 설정되지 않았습니다");
        }
    }
}
