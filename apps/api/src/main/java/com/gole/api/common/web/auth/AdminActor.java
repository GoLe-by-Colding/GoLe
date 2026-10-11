package com.gole.api.common.web.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 현재 요청의 조치자(관리자). admin 의 {@code AdminAuthInterceptor} 가 요청 속성에 넣어둔 값을 읽는다.
 *
 * <p>컨트롤러마다 토큰을 다시 파싱하지 않게 해, 인증 로직이 인터셉터 한 곳에만 존재하도록 유지한다.
 * 인터셉터를 통과한 요청에만 존재하므로 여기서는 null 방어만 한다.
 */
public record AdminActor(String id, String email) {

    /** 검증된 관리자 계정 id 를 담는 요청 속성 이름. */
    public static final String ATTR_ACCOUNT_ID = "gole.admin.accountId";

    /** 검증된 관리자 이메일을 담는 요청 속성 이름. */
    public static final String ATTR_ACCOUNT_EMAIL = "gole.admin.accountEmail";

    public static AdminActor of(HttpServletRequest request) {
        Object id = request.getAttribute(ATTR_ACCOUNT_ID);
        Object email = request.getAttribute(ATTR_ACCOUNT_EMAIL);
        return new AdminActor(id == null ? "" : id.toString(), email == null ? "" : email.toString());
    }
}
