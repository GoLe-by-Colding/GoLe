package com.gole.api.account.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;

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
}
