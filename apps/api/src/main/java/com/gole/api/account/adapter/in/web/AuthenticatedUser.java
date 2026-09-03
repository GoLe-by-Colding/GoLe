package com.gole.api.account.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** 인터셉터가 검증한 현재 사용자 식별자를 컨트롤러에 전달한다. */
public final class AuthenticatedUser {

    private AuthenticatedUser() {}

    public static String id(HttpServletRequest request) {
        Object accountId = request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID);
        if (accountId instanceof String id && !id.isBlank()) {
            return id;
        }
        throw new IllegalStateException("authenticated account attribute is missing");
    }

    /** 공개 조회에서도 유효한 세션이 함께 왔으면 개인화에 사용할 수 있다. */
    public static Optional<String> optionalId(HttpServletRequest request) {
        Object accountId = request.getAttribute(UserAuthInterceptor.ATTR_ACCOUNT_ID);
        if (accountId instanceof String id && !id.isBlank()) {
            return Optional.of(id);
        }
        return Optional.empty();
    }
}
