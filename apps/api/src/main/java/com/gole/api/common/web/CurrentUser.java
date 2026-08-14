package com.gole.api.common.web;

import com.gole.api.account.domain.model.Role;

/**
 * 인증된 요청자. 컨트롤러 파라미터로 선언하면 {@link CurrentUserArgumentResolver}가 채운다.
 *
 * <p>요청 본문이 아니라 <b>세션</b>에서 나온 신원이라는 점이 핵심이다. 본문으로 받은
 * {@code userId}는 사용자가 마음대로 바꿀 수 있으므로 신원이 아니라 주장일 뿐이다.
 */
public record CurrentUser(String accountId, String email, Role role) {}
