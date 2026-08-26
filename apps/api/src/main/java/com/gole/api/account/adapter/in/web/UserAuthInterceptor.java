package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 일반 사용자 API 가드. 쓰기 요청과 주문 조회를 서버 세션으로 인증한다. */
@Component
public class UserAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_ACCOUNT_ID = "gole.user.accountId";
    private final GetCurrentSessionUseCase sessions;
    private final SessionCookie sessionCookie;

    public UserAuthInterceptor(GetCurrentSessionUseCase sessions, SessionCookie sessionCookie) {
        this.sessions = sessions;
        this.sessionCookie = sessionCookie;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isPublicRead(request)) {
            return true;
        }
        String token = sessionCookie.resolve(request);
        CurrentSession session =
                sessions.resolve(token).orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다"));
        request.setAttribute(ATTR_ACCOUNT_ID, session.accountId());
        return true;
    }

    private static boolean isPublicRead(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        boolean privateRead = uri.startsWith("/api/v1/orders")
                || uri.startsWith("/api/v1/collections")
                || uri.startsWith("/api/v1/users/")
                || uri.startsWith("/api/v1/chat")
                // 매물 조회는 공개지만 "내 매물"만은 예외다. 세션으로 대상을 정하므로 여기서
                // 인증을 붙여야 하고, 빠지면 계정 속성이 없어 컨트롤러가 500으로 터진다.
                || uri.startsWith("/api/v1/listings/mine");
        return !privateRead;
    }
}
