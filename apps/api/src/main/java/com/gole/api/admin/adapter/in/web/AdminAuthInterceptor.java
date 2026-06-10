package com.gole.api.admin.adapter.in.web;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 가드. {@code /api/admin/**} 요청에 대해 Authorization: Bearer 토큰을 Redis 세션으로 해석하고
 * ADMIN 권한일 때만 통과시킨다. 그 외에는 401/403. 해석된 계정 id는 요청 속성으로 전달한다.
 *
 * <p>관리자 컨텍스트의 인바운드 어댑터로서 계정 컨텍스트의 인바운드 포트(세션 해석)에만 의존한다.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_ACCOUNT_ID = "gole.admin.accountId";

    private final GetCurrentSessionUseCase getCurrentSession;

    public AdminAuthInterceptor(GetCurrentSessionUseCase getCurrentSession) {
        this.getCurrentSession = getCurrentSession;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractBearer(request.getHeader("Authorization"));
        CurrentSession session = getCurrentSession
                .resolve(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다"));
        if (session.role() != Role.ADMIN) {
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
        request.setAttribute(ATTR_ACCOUNT_ID, session.accountId());
        return true;
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
