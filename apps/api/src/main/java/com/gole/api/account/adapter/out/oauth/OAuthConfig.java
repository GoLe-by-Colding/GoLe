package com.gole.api.account.adapter.out.oauth;

import com.gole.api.account.application.port.out.SocialIdentityProviderPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 소셜 로그인 어댑터 조립. OAuthProperties를 활성화하고 provider 포트 빈을 구성한다.
 */
@Configuration
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthConfig {

    @Bean
    public SocialIdentityProviderPort socialIdentityProviderPort(OAuthProperties properties) {
        return new RestClientSocialIdentityProviderAdapter(properties);
    }
}
