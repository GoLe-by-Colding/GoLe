package com.gole.api.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.in.web.SessionCookie;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminAuthInterceptorTest {

    private final GetCurrentSessionUseCase sessions = mock(GetCurrentSessionUseCase.class);
    private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor(sessions, new SessionCookie("false"));
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("세션이 없으면 관리자 요청을 인증 오류로 차단한다")
    void rejectsMissingSession() {
        MockHttpServletRequest request = request("GET", "/api/admin/overview", null);
        when(sessions.resolve("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(UnauthorizedException.class);

        verify(sessions).resolve("");
    }

    @Test
    @DisplayName("일반 회원 세션은 관리자 요청을 권한 오류로 차단한다")
    void rejectsNonAdminRole() {
        MockHttpServletRequest request = request("GET", "/api/admin/accounts", "Bearer user-token");
        when(sessions.resolve("user-token"))
                .thenReturn(Optional.of(new CurrentSession("account-1", "user@example.com", Role.USER)));

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("관리자 세션은 요청에 감사 로그용 조치자 정보를 전달한다")
    void allowsAdminAndSetsActorAttributes() {
        MockHttpServletRequest request = request("POST", "/api/admin/reports/report-1/resolve", "Bearer admin-token");
        when(sessions.resolve("admin-token"))
                .thenReturn(Optional.of(new CurrentSession("admin-1", "admin@example.com", Role.ADMIN)));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_ID)).isEqualTo("admin-1");
        assertThat(request.getAttribute(AdminAuthInterceptor.ATTR_ACCOUNT_EMAIL))
                .isEqualTo("admin@example.com");
    }

    @Test
    @DisplayName("CORS 프리플라이트는 세션 조회 없이 통과한다")
    void allowsCorsPreflightWithoutSessionLookup() {
        MockHttpServletRequest request = request("OPTIONS", "/api/admin/overview", null);
        request.addHeader("Origin", "http://localhost:3010");
        request.addHeader("Access-Control-Request-Method", "GET");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(sessions, never()).resolve("");
    }

    private static MockHttpServletRequest request(String method, String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
