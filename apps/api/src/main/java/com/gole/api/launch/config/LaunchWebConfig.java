package com.gole.api.launch.config;

import com.gole.api.launch.adapter.in.web.LaunchGateInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 거래 게이트를 주문·정산 경로에 등록한다.
 *
 * <p>등록 패턴은 넓게 두고 실제 차단 여부는 인터셉터가 판단한다. 무엇이 막히는지 읽을 자리가
 * 한곳으로 유지돼야, 단계를 내렸을 때 무엇이 닫히는지 코드를 훑지 않고 답할 수 있다.
 *
 * <p>PortOne 웹훅({@code /api/v1/payments/**})은 의도적으로 등록하지 않는다 — 이미 승인된
 * 결제의 반영을 막으면 돈만 빠져나간 상태가 된다.
 */
@Configuration
public class LaunchWebConfig implements WebMvcConfigurer {

    private final LaunchGateInterceptor launchGateInterceptor;

    public LaunchWebConfig(LaunchGateInterceptor launchGateInterceptor) {
        this.launchGateInterceptor = launchGateInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(launchGateInterceptor)
                .addPathPatterns("/api/v1/orders", "/api/v1/orders/**", "/api/v1/reviews", "/api/v1/reviews/**");
    }
}
