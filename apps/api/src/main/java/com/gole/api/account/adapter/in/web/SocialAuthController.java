package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.PublicAuthRequestLimitUseCase;
import com.gole.api.account.application.port.in.SocialLoginUseCase;
import com.gole.api.account.application.port.in.SocialLoginUseCase.AuthorizeUrlResult;
import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginCommand;
import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginResult;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import com.gole.api.common.exception.BadRequestException;
import com.gole.api.common.web.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 소셜 로그인(OAuth2). (소셜 로그인 스펙 S3~S6)
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class SocialAuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final SessionCookie sessionCookie;
    private final OAuthTransactionCookie oauthTransactionCookie;
    private final PublicAuthRequestLimitUseCase publicRequestLimit;
    private final ClientAddressResolver clientAddresses;

    public SocialAuthController(
            SocialLoginUseCase socialLoginUseCase,
            SessionCookie sessionCookie,
            OAuthTransactionCookie oauthTransactionCookie,
            PublicAuthRequestLimitUseCase publicRequestLimit,
            ClientAddressResolver clientAddresses) {
        this.socialLoginUseCase = socialLoginUseCase;
        this.sessionCookie = sessionCookie;
        this.oauthTransactionCookie = oauthTransactionCookie;
        this.publicRequestLimit = publicRequestLimit;
        this.clientAddresses = clientAddresses;
    }

    /** 활성(설정된) provider 목록. 프론트가 버튼 노출 여부를 결정한다. (S3) */
    @GetMapping("/providers")
    public List<String> providers() {
        return socialLoginUseCase.enabledProviders().stream()
                .map(AuthProvider::key)
                .toList();
    }

    /** provider 동의 화면 URL. 서버가 state를 발급한다. (S4) */
    @PostMapping("/{provider}/authorize-url")
    public AuthorizeUrlResponse authorizeUrl(
            @PathVariable String provider,
            @Valid @RequestBody AuthorizeUrlRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        publicRequestLimit.acquireOAuthAuthorization(clientAddresses.resolve(http));
        AuthProvider parsed = parse(provider);
        SignupPolicyAcceptance acceptance = policyAcceptance(
                request.termsVersion(),
                request.privacyVersion(),
                request.termsAccepted(),
                request.privacyAcknowledged(),
                request.minimumAgeConfirmed(),
                request.thirdPartyProvisionVersion(),
                request.thirdPartyProvisionAccepted());
        AuthorizeUrlResult result =
                socialLoginUseCase.authorizeUrl(parsed, request.redirectUri(), acceptance, request.returnTo());
        oauthTransactionCookie.issue(http, response, result.state());
        return new AuthorizeUrlResponse(result.url());
    }

    /** code 교환 → state 검증 → find-or-create → 세션 발급. (S5, S6) */
    @PostMapping("/{provider}/callback")
    public SocialLoginResponse callback(
            @PathVariable String provider,
            @Valid @RequestBody CallbackRequest request,
            HttpServletRequest http,
            HttpServletResponse response) {
        AuthProvider parsed = parse(provider);
        if (!oauthTransactionCookie.matches(http, request.state())) {
            oauthTransactionCookie.clear(http, response);
            throw new BadRequestException("OAUTH_STATE_INVALID", "유효하지 않은 로그인 요청입니다");
        }
        try {
            SocialLoginResult result = socialLoginUseCase.login(
                    new SocialLoginCommand(parsed, request.code(), request.redirectUri(), request.state()));
            sessionCookie.issue(http, response, result.sessionToken());
            return new SocialLoginResponse(
                    result.accountId(),
                    result.sessionToken(),
                    result.role().name(),
                    result.newAccount(),
                    result.onboardingRequired(),
                    result.returnTo());
        } finally {
            // 성공·실패와 무관하게 브라우저 결박 쿠키도 한 번만 사용한다.
            oauthTransactionCookie.clear(http, response);
        }
    }

    private static AuthProvider parse(String provider) {
        return AuthProvider.from(provider)
                .orElseThrow(() ->
                        new BadRequestException("OAUTH_PROVIDER_UNSUPPORTED", "Unsupported provider: " + provider));
    }

    private static SignupPolicyAcceptance policyAcceptance(
            String termsVersion,
            String privacyVersion,
            Boolean termsAccepted,
            Boolean privacyAcknowledged,
            Boolean minimumAgeConfirmed,
            String thirdPartyProvisionVersion,
            Boolean thirdPartyProvisionAccepted) {
        boolean omitted = termsVersion == null
                && privacyVersion == null
                && termsAccepted == null
                && privacyAcknowledged == null
                && minimumAgeConfirmed == null
                && thirdPartyProvisionVersion == null
                && thirdPartyProvisionAccepted == null;
        if (omitted) {
            return null;
        }
        return new SignupPolicyAcceptance(
                termsVersion,
                privacyVersion,
                Boolean.TRUE.equals(termsAccepted),
                Boolean.TRUE.equals(privacyAcknowledged),
                Boolean.TRUE.equals(minimumAgeConfirmed),
                thirdPartyProvisionVersion,
                Boolean.TRUE.equals(thirdPartyProvisionAccepted));
    }

    public record AuthorizeUrlResponse(String url) {}

    /** 정책 확인 정보는 URL·프록시 access log에 남지 않도록 JSON 본문으로만 받는다. */
    public record AuthorizeUrlRequest(
            @NotBlank String redirectUri,
            String termsVersion,
            String privacyVersion,
            Boolean termsAccepted,
            Boolean privacyAcknowledged,
            Boolean minimumAgeConfirmed,
            String thirdPartyProvisionVersion,
            Boolean thirdPartyProvisionAccepted,
            String returnTo) {}

    public record CallbackRequest(@NotBlank String code, @NotBlank String redirectUri, @NotBlank String state) {}

    public record SocialLoginResponse(
            String accountId,
            String sessionToken,
            String role,
            boolean newAccount,
            boolean onboardingRequired,
            String returnTo) {}
}
