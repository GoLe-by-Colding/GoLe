package com.gole.api.admin.adapter.in.web;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.ForbiddenException;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 가드. {@code /api/admin/**} 요청에 대해 Authorization: Bearer 토큰을 Redis 세션으로 해석하고
 * ADMIN 권한일 때만 통과시킨다. 그 외에는 401/403. 해석된 계정 id는 요청 속성으로 전달한다.
 *
 * <p>관리자 컨텍스트의 인바운드 어댑터로서 계정 컨텍스트의 인바운드 포트(세션 해석)에만 의존한다.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    public static final String ATTR_ACCOUNT_ID = "gole.admin.accountId";
    public static final String ATTR_ACCOUNT_EMAIL = "gole.admin.accountEmail";

    private final GetCurrentSessionUseCase getCurrentSession;

    public AdminAuthInterceptor(GetCurrentSessionUseCase getCurrentSession) {
        this.getCurrentSession = getCurrentSession;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 프리플라이트(OPTIONS)는 브라우저가 Authorization 헤더 없이 보낸다.
        // 여기서 막으면 본 요청이 시작조차 못 해, 브라우저에서만 조용히 실패한다.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String token = extractBearer(request.getHeader("Authorization"));
        CurrentSession session = getCurrentSession.resolve(token).orElseThrow(() -> {
            // 토큰·이메일·IP는 남기지 않는다. 운영 분석에 필요한 요청 경로와 거부 사유만 기록한다.
            log.warn(
                    "[Admin access denied] reason=invalid_session method={} path={}",
                    request.getMethod(),
                    request.getRequestURI());
            return new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다");
        });
        if (session.role() != Role.ADMIN) {
            log.warn(
                    "[Admin access denied] reason=insufficient_role accountId={} role={} method={} path={}",
                    session.accountId(),
                    session.role(),
                    request.getMethod(),
                    request.getRequestURI());
            throw new ForbiddenException("ADMIN_ONLY", "관리자 권한이 필요합니다");
        }
        // 감사 로그의 조치자 스냅샷으로 쓰이도록 id와 이메일을 함께 전달한다. (요구사항 8.1)
        request.setAttribute(ATTR_ACCOUNT_ID, session.accountId());
        request.setAttribute(ATTR_ACCOUNT_EMAIL, session.email());
        return true;
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
