package com.gole.api.common.web;

import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.common.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code Authorization: Bearer} 토큰을 {@link CurrentUser}로 해석한다.
 *
 * <p>인터셉터가 아니라 <b>인자 리졸버</b>인 이유: 경로 패턴으로 일괄 차단하면 공개 조회
 * (매물·시세·커뮤니티 읽기)까지 함께 막힌다. 보호가 필요한 핸들러가 파라미터로 선언하게 하면
 * 어떤 엔드포인트가 인증을 요구하는지 시그니처만 봐도 드러난다.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER = "Bearer ";

    private final GetCurrentSessionUseCase getCurrentSession;

    public CurrentUserArgumentResolver(GetCurrentSessionUseCase getCurrentSession) {
        this.getCurrentSession = getCurrentSession;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return require(extractBearer(webRequest.getHeader("Authorization")));
    }

    /**
     * 토큰 문자열로 직접 해석한다.
     *
     * <p>브라우저 {@code EventSource}는 헤더를 붙일 수 없어 SSE 엔드포인트만 쿼리 파라미터로
     * 토큰을 받는다. 그 경로에서 이 메서드를 쓴다.
     */
    public CurrentUser require(String token) {
        CurrentSession session = getCurrentSession
                .resolve(token)
                .orElseThrow(() -> new UnauthorizedException("INVALID_SESSION", "로그인이 필요합니다"));
        return new CurrentUser(session.accountId(), session.email(), session.role());
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return "";
        }
        return authorization.substring(BEARER.length()).trim();
    }
}
