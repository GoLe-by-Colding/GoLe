package com.gole.api.promotion.config;

import com.gole.api.promotion.adapter.in.web.PromotionAgentTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 홍보 납품 창구 가드를 등록한다(promotion-review D29). {@code /api/internal/**} 에는 사용자·관리자 세션 가드가 없다 —
 * 이 경로는 홍보 토큰 하나로만 열린다.
 */
@Configuration
public class PromotionAgentWebConfig implements WebMvcConfigurer {

    private final PromotionAgentTokenInterceptor tokenInterceptor;

    public PromotionAgentWebConfig(PromotionAgentTokenInterceptor tokenInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor).addPathPatterns("/api/internal/promotion-agent/**");
    }
}
