package com.gole.api.account.config;

import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 공개 조회를 제외한 사용자 API에 세션 가드를 등록한다. */
@Configuration
public class UserWebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;

    public UserWebConfig(UserAuthInterceptor userAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/v1/**", "/api/v2/**")
                .excludePathPatterns("/api/v1/accounts/**", "/api/v1/auth/**", "/api/v1/payments/portone/webhook");
    }
}
