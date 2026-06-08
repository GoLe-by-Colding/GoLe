package com.gole.api.account.adapter.out.oauth;

import com.gole.api.account.adapter.out.oauth.OAuthProperties.Registration;
import com.gole.api.account.application.port.out.SocialIdentityProviderPort;
import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.common.exception.BadRequestException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth2 provider 연동 어댑터(Spring {@link RestClient}). token 교환 + userinfo 조회를 수행하고
 * provider별 응답에서 providerId/email을 파싱한다. (소셜 로그인 스펙 S4, S5)
 *
 * <p>provider별 token/userinfo URL과 client 정보는 {@link OAuthProperties}로 주입되며,
 * client-id만 env로 채우면 동작한다.
 */
public class RestClientSocialIdentityProviderAdapter implements SocialIdentityProviderPort {

    private final OAuthProperties properties;
    private final RestClient restClient;

    public RestClientSocialIdentityProviderAdapter(OAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isConfigured(AuthProvider provider) {
        Registration reg = properties.registration(provider.key());
        return reg != null && reg.isConfigured();
    }

    @Override
    public String authorizeUrl(AuthProvider provider, String redirectUri, String state) {
        Registration reg = require(provider);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(reg.authorizationUri())
                .queryParam("client_id", reg.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code");
        if (reg.scope() != null && !reg.scope().isBlank()) {
            builder.queryParam("scope", reg.scope());
        }
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }
        return builder.build().toUriString();
    }

    @Override
    public SocialProfile fetchProfile(AuthProvider provider, String code, String redirectUri) {
        Registration reg = require(provider);
        String accessToken = exchangeCodeForToken(reg, code, redirectUri);
        Map<String, Object> userInfo = fetchUserInfo(reg, accessToken);
        return parseProfile(provider, userInfo);
    }

    private String exchangeCodeForToken(Registration reg, String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", reg.clientId());
        form.add("client_secret", reg.clientSecret() == null ? "" : reg.clientSecret());
        form.add("redirect_uri", redirectUri);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> token = restClient.post()
                    .uri(reg.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (token == null || token.get("access_token") == null) {
                throw new BadRequestException("OAUTH_EXCHANGE_FAILED", "No access token from provider");
            }
            return String.valueOf(token.get("access_token"));
        } catch (RestClientException e) {
            throw new BadRequestException("OAUTH_EXCHANGE_FAILED", "Token exchange failed: " + e.getMessage());
        }
    }

    private Map<String, Object> fetchUserInfo(Registration reg, String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(reg.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw new BadRequestException("OAUTH_PROFILE_FAILED", "Empty profile from provider");
            }
            return body;
        } catch (RestClientException e) {
            throw new BadRequestException("OAUTH_PROFILE_FAILED", "Profile fetch failed: " + e.getMessage());
        }
    }

    /** provider별 응답 스키마에서 providerId/email을 추출한다. */
    private SocialProfile parseProfile(AuthProvider provider, Map<String, Object> info) {
        return switch (provider) {
            case GOOGLE -> new SocialProfile(
                    provider, str(info.get("sub")), str(info.get("email")));
            case KAKAO -> {
                Map<String, Object> account = asMap(info.get("kakao_account"));
                yield new SocialProfile(
                        provider, str(info.get("id")), account == null ? null : str(account.get("email")));
            }
            case NAVER -> {
                Map<String, Object> resp = asMap(info.get("response"));
                yield resp == null
                        ? new SocialProfile(provider, null, null)
                        : new SocialProfile(provider, str(resp.get("id")), str(resp.get("email")));
            }
        };
    }

    private Registration require(AuthProvider provider) {
        Registration reg = properties.registration(provider.key());
        if (reg == null || !reg.isConfigured()) {
            throw new BadRequestException(
                    "OAUTH_PROVIDER_NOT_CONFIGURED", provider.key() + " is not configured");
        }
        return reg;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
