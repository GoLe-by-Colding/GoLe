package com.gole.api.common.web.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 인터셉터가 검증한 현재 사용자 식별자를 컨트롤러에 전달한다.
 *
 * <p>세션 해석은 account 의 {@code UserAuthInterceptor} 가 하고, 모든 컨텍스트의 컨트롤러는 이 클래스로만 결과를 읽는다.
 * 공통 웹 관심사라 account 어댑터가 아니라 common 에 둔다 — 컨트롤러가 account 의 어댑터 패키지에 의존하지 않게 한다.
 */
public final class AuthenticatedUser {

    /** 검증된 계정 id 를 담는 요청 속성 이름. */
    public static final String ATTRIBUTE = "gole.user.accountId";

    private AuthenticatedUser() {}

    public static String id(HttpServletRequest request) {
        Object accountId = request.getAttribute(ATTRIBUTE);
        if (accountId instanceof String id && !id.isBlank()) {
            return id;
        }
        throw new IllegalStateException("authenticated account attribute is missing");
    }

    /** 공개 조회에서도 유효한 세션이 함께 왔으면 개인화에 사용할 수 있다. */
    public static Optional<String> optionalId(HttpServletRequest request) {
        Object accountId = request.getAttribute(ATTRIBUTE);
        if (accountId instanceof String id && !id.isBlank()) {
            return Optional.of(id);
        }
        return Optional.empty();
    }
}
