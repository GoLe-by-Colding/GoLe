package com.gole.api.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase;
import com.gole.api.account.application.port.in.GetOnboardingStatusUseCase.OnboardingStatus;
import com.gole.api.account.domain.exception.OnboardingRequiredException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * 거래성 액션 게이트 검증. (onboarding D5, R9)
 *
 * <p>이 저장소는 클라이언트만 믿고 게이트를 걸었다가 실제 우회 사고가 난 전례가 있다.
 * 그래서 "서버가 정말 막는가"와 "막지 말아야 할 것을 막지 않는가"를 둘 다 본다.
 */
class OnboardingGuardInterceptorTest {

    private final GetOnboardingStatusUseCase onboardingStatus = mock(GetOnboardingStatusUseCase.class);
    private final OnboardingGuardInterceptor interceptor = new OnboardingGuardInterceptor(onboardingStatus);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void completedAccountPassesGuardedAction() {
        MockHttpServletRequest request = authenticated("account-1");
        when(onboardingStatus.status("account-1")).thenReturn(status(false, false));

        assertThat(interceptor.preHandle(request, response, guardedHandler())).isTrue();
    }

    @Test
    void incompleteAccountIsBlockedWith403Code() {
        MockHttpServletRequest request = authenticated("account-1");
        when(onboardingStatus.status("account-1")).thenReturn(status(true, false));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, guardedHandler()))
                .isInstanceOf(OnboardingRequiredException.class)
                .hasFieldOrPropertyWithValue("code", "ONBOARDING_REQUIRED");
    }

    @Test
    void legacyExemptAccountIsNeverBlocked() {
        // D6: 배포 이전 가입자는 필드가 하나도 없어도 통과한다. required가 이미 false다.
        MockHttpServletRequest request = authenticated("legacy-1");
        when(onboardingStatus.status("legacy-1")).thenReturn(status(false, true));

        assertThat(interceptor.preHandle(request, response, guardedHandler())).isTrue();
    }

    @Test
    void unguardedActionIsNotEvenChecked() {
        // 홈·매물조회·시세 등 둘러보기는 온보딩과 무관하게 항상 허용한다(D5).
        MockHttpServletRequest request = authenticated("account-1");

        assertThat(interceptor.preHandle(request, response, unguardedHandler())).isTrue();
        verify(onboardingStatus, never()).status(anyString());
    }

    @Test
    void nonHandlerMethodIsIgnored() {
        // 정적 리소스 핸들러 등은 HandlerMethod가 아니다.
        assertThat(interceptor.preHandle(authenticated("account-1"), response, new Object()))
                .isTrue();
        verify(onboardingStatus, never()).status(anyString());
    }

    @Test
    void corsPreflightIsNotBlocked() {
        // 브라우저는 프리플라이트를 Authorization 없이 보낸다. 여기서 막으면 본 요청이
        // 시작조차 못 하고 브라우저에서만 조용히 실패한다.
        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/listings");

        assertThat(interceptor.preHandle(preflight, response, guardedHandler())).isTrue();
        verify(onboardingStatus, never()).status(anyString());
    }

    @Test
    void missingAccountAttributeIsTreatedAsBlocked() {
        // 판정 불가를 통과로 해석하면 게이트가 조용히 비활성화된다.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, guardedHandler()))
                .isInstanceOf(OnboardingRequiredException.class);
        verify(onboardingStatus, never()).status(anyString());
    }

    private static MockHttpServletRequest authenticated(String accountId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/listings");
        request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, accountId);
        return request;
    }

    private static OnboardingStatus status(boolean required, boolean legacyExempt) {
        return new OnboardingStatus(
                "account-1",
                true,
                "고레",
                true,
                true,
                "010-****-5678",
                true,
                List.of("technic"),
                true,
                false,
                required,
                legacyExempt);
    }

    private static HandlerMethod guardedHandler() {
        return handler("guarded");
    }

    private static HandlerMethod unguardedHandler() {
        return handler("unguarded");
    }

    private static HandlerMethod handler(String methodName) {
        try {
            Method method = SampleController.class.getMethod(methodName);
            return new HandlerMethod(new SampleController(), method);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 애노테이션 유무만 다른 두 핸들러. 실제 컨트롤러를 끌어오지 않기 위한 최소 대역이다. */
    static class SampleController {

        @RequiresOnboarding
        public void guarded() {}

        public void unguarded() {}
    }
}
