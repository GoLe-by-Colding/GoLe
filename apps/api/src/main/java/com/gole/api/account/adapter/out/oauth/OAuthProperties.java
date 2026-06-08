package com.gole.api.account.adapter.out.oauth;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 provider 설정. (소셜 로그인 스펙 — 설정 외부화)
 *
 * <p>{@code oauth.providers.<google|kakao|naver>.*}. client-id/secret은 env로 주입한다.
 * client-id가 비어 있으면 해당 provider는 비활성으로 간주한다(S2).
 */
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Map<String, Registration> providers) {

    public OAuthProperties {
        providers = providers == null ? Map.of() : providers;
    }

    public Registration registration(String key) {
        return providers.get(key);
    }

    public record Registration(
            String clientId,
            String clientSecret,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String scope) {

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank();
        }
    }
}
