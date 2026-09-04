package com.gole.api.account.application.service;

import com.gole.api.common.exception.BadRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OAuth code가 돌아올 수 있는 URI를 서버의 정확한 허용목록으로 제한한다. */
@Component
public class OAuthRedirectUriPolicy {

    private static final Set<String> DEVELOPER_ENVIRONMENTS = Set.of("local", "development", "dev", "test", "e2e");
    private static final Set<RedirectUri> PUBLIC_REDIRECT_URIS = Set.of(
            parse("https://gole.co.kr/auth/callback/google"),
            parse("https://gole.co.kr/auth/callback/kakao"),
            parse("https://gole.co.kr/auth/callback/naver"));

    private final Set<RedirectUri> allowed;

    @Autowired
    public OAuthRedirectUriPolicy(
            @Value("${gole.oauth.allowed-redirect-uris}") String configuredUris,
            @Value("${gole.environment:local}") String environment) {
        if (configuredUris == null || configuredUris.isBlank()) {
            throw new IllegalStateException("OAuth redirect URI allowlist must not be empty");
        }
        try {
            this.allowed = Arrays.stream(configuredUris.split(",", -1))
                    .map(String::trim)
                    .map(OAuthRedirectUriPolicy::parseConfigured)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException invalidConfiguration) {
            throw new IllegalStateException(
                    "OAuth redirect URI allowlist contains an invalid entry", invalidConfiguration);
        }
        if (allowed.isEmpty()) {
            throw new IllegalStateException("OAuth redirect URI allowlist must not be empty");
        }
        if (requiresPublicSafety(environment) && !PUBLIC_REDIRECT_URIS.equals(allowed)) {
            throw new IllegalStateException("Public OAuth redirect URI allowlist must contain only GoLe callbacks");
        }
    }

    OAuthRedirectUriPolicy(String configuredUris) {
        this(configuredUris, "local");
    }

    public void requireAllowed(String redirectUri) {
        RedirectUri candidate;
        try {
            candidate = parse(redirectUri);
        } catch (IllegalArgumentException invalidRequest) {
            throw rejected();
        }
        if (!allowed.contains(candidate)) {
            throw rejected();
        }
    }

    private static RedirectUri parseConfigured(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("blank OAuth redirect URI");
        }
        return parse(value);
    }

    private static RedirectUri parse(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("invalid OAuth redirect URI");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
            boolean supportedScheme = scheme.equals("https") || (scheme.equals("http") && loopback);
            if (!supportedScheme
                    || host.isBlank()
                    || uri.isOpaque()
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || path == null
                    || path.isBlank()
                    || !path.startsWith("/")
                    || path.contains("\\")
                    || !uri.normalize().getRawPath().equals(path)) {
                throw new IllegalArgumentException("invalid OAuth redirect URI");
            }
            int port = effectivePort(scheme, uri.getPort());
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("invalid OAuth redirect URI port");
            }
            return new RedirectUri(scheme, host, port, path);
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("invalid OAuth redirect URI", invalid);
        }
    }

    private static int effectivePort(String scheme, int explicitPort) {
        if (explicitPort != -1) {
            return explicitPort;
        }
        return scheme.equals("https") ? 443 : 80;
    }

    private static BadRequestException rejected() {
        // 요청값 자체는 로그나 응답에 되비추지 않는다.
        return new BadRequestException("OAUTH_REDIRECT_URI_INVALID", "허용되지 않은 OAuth 리다이렉트 URI입니다");
    }

    private static boolean requiresPublicSafety(String environment) {
        String normalized = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        return !DEVELOPER_ENVIRONMENTS.contains(normalized);
    }

    private record RedirectUri(String scheme, String host, int port, String path) {}
}
