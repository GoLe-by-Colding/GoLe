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
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = sessionCookie.resolve(request);
        if (isPublicRead(request)) {
            // 공개 조회는 세션이 없어도 허용하되, 유효한 세션이 함께 오면 좋아요 등 개인화 상태를
            // 응답할 수 있도록 actor를 붙인다. 만료 토큰 때문에 공개 페이지가 막히면 안 된다.
            if (!token.isBlank()) {
                sessions.resolve(token)
                        .ifPresent(session -> request.setAttribute(ATTR_ACCOUNT_ID, session.accountId()));
            }
            return true;
        }
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
                || uri.startsWith("/api/v1/community/feed/following")
                // 매물 조회는 공개지만 "내 매물"만은 예외다. 세션으로 대상을 정하므로 여기서
                // 인증을 붙여야 하고, 빠지면 계정 속성이 없어 컨트롤러가 500으로 터진다.
                || uri.startsWith("/api/v1/listings/mine");
        return !privateRead;
    }
}
