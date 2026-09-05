package com.gole.api.account.config;

import com.gole.api.account.adapter.in.web.AccountMutationGateInterceptor;
import com.gole.api.account.adapter.in.web.OnboardingGuardInterceptor;
import com.gole.api.account.adapter.in.web.SellerIdentityGuardInterceptor;
import com.gole.api.account.adapter.in.web.UserAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 공개 조회를 제외한 사용자 API에 세션 가드를 등록한다. */
@Configuration
public class UserWebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final OnboardingGuardInterceptor onboardingGuardInterceptor;
    private final AccountMutationGateInterceptor accountMutationGateInterceptor;
    private final SellerIdentityGuardInterceptor sellerIdentityGuardInterceptor;

    public UserWebConfig(
            UserAuthInterceptor userAuthInterceptor,
            OnboardingGuardInterceptor onboardingGuardInterceptor,
            AccountMutationGateInterceptor accountMutationGateInterceptor,
            SellerIdentityGuardInterceptor sellerIdentityGuardInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
        this.onboardingGuardInterceptor = onboardingGuardInterceptor;
        this.accountMutationGateInterceptor = accountMutationGateInterceptor;
        this.sellerIdentityGuardInterceptor = sellerIdentityGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /accounts/**의 직접 세션 해석 경로까지 포함해야 하므로 인증 가드보다 먼저 전 API에
        // 등록한다. 유효한 세션이 없는 공개 요청은 인터셉터 내부에서 그대로 통과한다.
        registry.addInterceptor(accountMutationGateInterceptor)
                .addPathPatterns("/api/v1/**", "/api/v2/**", "/api/admin/**");
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/v1/**", "/api/v2/**")
                .excludePathPatterns(
                        "/api/v1/accounts/**",
                        "/api/v1/auth/**",
                        "/api/v1/policies/**",
                        "/api/v1/payments/portone/webhook");
        // 인증 가드 뒤에 온다 — 계정 속성이 채워진 뒤라야 온보딩 상태를 물어볼 수 있다.
        // 대상 선별은 경로가 아니라 @RequiresOnboarding 애노테이션이 한다(D5).
        registry.addInterceptor(onboardingGuardInterceptor).addPathPatterns("/api/v1/**", "/api/v2/**");
        // 판매자 신원확인은 일반 온보딩 면제와 무관하며, 인증된 전화번호와 운영 준비 플래그를
        // 모두 확인한다. @RequiresVerifiedSellerIdentity가 붙은 신규 판매 액션에만 적용한다.
        registry.addInterceptor(sellerIdentityGuardInterceptor).addPathPatterns("/api/v1/**", "/api/v2/**");
    }
}
