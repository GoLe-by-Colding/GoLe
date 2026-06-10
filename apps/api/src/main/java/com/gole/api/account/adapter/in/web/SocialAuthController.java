package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.SocialLoginUseCase;
import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginCommand;
import com.gole.api.account.application.port.in.SocialLoginUseCase.SocialLoginResult;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.common.exception.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 어댑터(REST): 소셜 로그인(OAuth2). (소셜 로그인 스펙 S3~S6)
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class SocialAuthController {

    private final SocialLoginUseCase socialLoginUseCase;

    public SocialAuthController(SocialLoginUseCase socialLoginUseCase) {
        this.socialLoginUseCase = socialLoginUseCase;
    }

    /** 활성(설정된) provider 목록. 프론트가 버튼 노출 여부를 결정한다. (S3) */
    @GetMapping("/providers")
    public List<String> providers() {
        return socialLoginUseCase.enabledProviders().stream()
                .map(AuthProvider::key)
                .toList();
    }

    /** provider 동의 화면 URL. 서버가 state를 발급한다. (S4) */
    @GetMapping("/{provider}/authorize-url")
    public AuthorizeUrlResponse authorizeUrl(
            @PathVariable String provider, @RequestParam("redirectUri") String redirectUri) {
        AuthProvider parsed = parse(provider);
        return new AuthorizeUrlResponse(socialLoginUseCase.authorizeUrl(parsed, redirectUri));
    }

    /** code 교환 → state 검증 → find-or-create → 세션 발급. (S5, S6) */
    @PostMapping("/{provider}/callback")
    public SocialLoginResponse callback(@PathVariable String provider, @Valid @RequestBody CallbackRequest request) {
        AuthProvider parsed = parse(provider);
        SocialLoginResult result = socialLoginUseCase.login(
                new SocialLoginCommand(parsed, request.code(), request.redirectUri(), request.state()));
        return new SocialLoginResponse(
                result.accountId(), result.sessionToken(), result.role().name(), result.newAccount());
    }

    private static AuthProvider parse(String provider) {
        return AuthProvider.from(provider)
                .orElseThrow(() ->
                        new BadRequestException("OAUTH_PROVIDER_UNSUPPORTED", "Unsupported provider: " + provider));
    }

    public record AuthorizeUrlResponse(String url) {}

    public record CallbackRequest(@NotBlank String code, @NotBlank String redirectUri, @NotBlank String state) {}

    public record SocialLoginResponse(String accountId, String sessionToken, String role, boolean newAccount) {}
}
