package com.gole.api.account.adapter.in.web;

import com.gole.api.account.application.concurrency.AccountMutationGate;
import com.gole.api.account.application.concurrency.AccountMutationGate.Lease;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase;
import com.gole.api.account.application.port.in.GetCurrentSessionUseCase.CurrentSession;
import com.gole.api.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 유효한 세션이 실린 변경 요청을 계정별 공유 lease로 요청 완료까지 감싼다.
 *
 * <p>{@code /api/v1/accounts/**}는 가입 같은 공개 API 때문에 일반 사용자 인증 가드에서 제외되어
 * 있다. 이 인터셉터는 경로 예외를 두지 않고 세션 자체를 선택적으로 해석해 온보딩·동의·비밀번호
 * 변경·refresh/logout까지 같은 경계에 포함한다. 탈퇴 요청만 컨트롤러가 배타 lease를 직접 얻는다.
 */
@Component
public class AccountMutationGateInterceptor implements AsyncHandlerInterceptor {

    static final String ATTR_LEASE = "gole.account.mutationGateLease";
    static final String DELETION_REQUEST_PATH = "/api/v1/accounts/me/deletion-requests";

    private final AccountMutationGate gate;
    private final GetCurrentSessionUseCase sessions;
    private final SessionCookie sessionCookie;

    public AccountMutationGateInterceptor(
            AccountMutationGate gate, GetCurrentSessionUseCase sessions, SessionCookie sessionCookie) {
        this.gate = gate;
        this.sessions = sessions;
        this.sessionCookie = sessionCookie;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isUnsafe(request) || isDeletionRequest(request) || request.getAttribute(ATTR_LEASE) != null) {
            return true;
        }

        String token = sessionCookie.resolve(request);
        Optional<CurrentSession> before = sessions.resolve(token);
        if (before.isEmpty()) {
            // 공개 가입·인증·비밀번호 재설정 등은 기존 공개 경계가 처리한다. 유효한 세션을
            // 동반한 요청만 이 계정별 gate의 대상이다.
            return true;
        }

        CurrentSession expected = before.get();
        Lease lease = gate.acquireShared(expected.accountId());
        try {
            CurrentSession revalidated = sessions.resolve(token)
                    .filter(session -> expected.accountId().equals(session.accountId()))
                    .orElseThrow(AccountMutationGateInterceptor::invalidSession);
            request.setAttribute(ATTR_LEASE, lease);
            request.setAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID, revalidated.accountId());
            return true;
        } catch (RuntimeException | Error exception) {
            lease.close();
            throw exception;
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object value = request.getAttribute(ATTR_LEASE);
        if (value instanceof Lease lease) {
            request.removeAttribute(ATTR_LEASE);
            lease.close();
        }
    }

    private static boolean isUnsafe(HttpServletRequest request) {
        String method = request.getMethod();
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private static boolean isDeletionRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return HttpMethod.POST.matches(request.getMethod()) && DELETION_REQUEST_PATH.equals(path);
    }

    private static UnauthorizedException invalidSession() {
        return new UnauthorizedException("INVALID_SESSION", "유효한 세션이 아닙니다");
    }
}
