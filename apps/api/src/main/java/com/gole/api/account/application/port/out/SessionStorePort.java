package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Role;
import java.time.Duration;
import java.util.Optional;

/**
 * 세션 저장소 outbound port. 발급된 세션 토큰을 계정/권한과 매핑해 저장하고 해석한다.
 * 이를 통해 불투명 토큰이 실제로 검증 가능한 세션이 된다(스테이트풀 세션).
 */
public interface SessionStorePort {

    /** 토큰→(계정,권한) 매핑을 TTL과 함께 저장한다. */
    void store(String token, String accountId, Role role, Duration ttl);

    /** 토큰을 해석한다. 없거나 만료면 비어있음. */
    Optional<SessionPrincipal> resolve(String token);

    /** 토큰을 폐기한다(로그아웃). */
    void revoke(String token);

    /** 인증된 호출자 식별 정보. */
    record SessionPrincipal(String accountId, Role role) {
    }
}
