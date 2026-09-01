package com.gole.api.account.config;

import com.gole.api.account.adapter.in.web.OnboardingGuardInterceptor;
import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 공개 조회를 제외한 사용자 API에 세션 가드를 등록한다. */
@Configuration
public class UserWebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final OnboardingGuardInterceptor onboardingGuardInterceptor;

    public UserWebConfig(
            UserAuthInterceptor userAuthInterceptor, OnboardingGuardInterceptor onboardingGuardInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
        this.onboardingGuardInterceptor = onboardingGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/v1/**", "/api/v2/**")
                .excludePathPatterns("/api/v1/accounts/**", "/api/v1/auth/**", "/api/v1/payments/portone/webhook");
        // 인증 가드 뒤에 온다 — 계정 속성이 채워진 뒤라야 온보딩 상태를 물어볼 수 있다.
        // 대상 선별은 경로가 아니라 @RequiresOnboarding 애노테이션이 한다(D5).
        registry.addInterceptor(onboardingGuardInterceptor).addPathPatterns("/api/v1/**", "/api/v2/**");
    }
}
