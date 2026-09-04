package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase;
import com.gole.api.account.domain.exception.OnboardingRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RequiresOnboarding}가 붙은 핸들러의 온보딩 완료 여부 가드. (onboarding D5, R9)
 *
 * <p>판정은 account 컨텍스트의 인바운드 포트({@link GetOnboardingStatusUseCase})만 호출한다 —
 * 저장소나 Document를 직접 보면 다른 컨텍스트가 계정 스키마에 묶인다.
 *
 * <p>인증 가드가 인터셉터인 것과 같은 이유로 여기도 Aspect가 아니다 — 어떤 HTTP 요청인지
 * (메서드·핸들러 애노테이션)를 알아야 판단할 수 있다.
 */
@Component
public class OnboardingGuardInterceptor implements HandlerInterceptor {

    private final GetOnboardingStatusUseCase onboardingStatus;

    public OnboardingGuardInterceptor(GetOnboardingStatusUseCase onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 프리플라이트는 Authorization 없이 오므로 여기서 막으면 본 요청이 시작조차 못 한다.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(RequiresOnboarding.class)) {
            return true;
        }
        Object accountId = request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID);
        if (!(accountId instanceof String id) || id.isBlank()) {
            // 인증 가드가 먼저 돌아 401을 냈어야 하는 경로다. 여기서 통과시키면
            // 게이트가 조용히 비활성화되므로, 판정 불가는 차단으로 해석한다.
            throw new OnboardingRequiredException();
        }
        // legacyExempt 계정은 status.required()가 항상 false다(D6) — 여기서 따로 분기하지 않는다.
        if (onboardingStatus.status(id).required()) {
            throw new OnboardingRequiredException();
        }
        return true;
    }
}
