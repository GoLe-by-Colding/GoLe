package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.AuthProvider;
import java.time.Duration;

/**
 * Outbound port: OAuth state(CSRF 방지) 저장소. 서버가 state를 발급·저장하고 콜백에서 1회 소비한다.
 * (소셜 로그인 보안 강화 — 서버측 state 검증)
 */
public interface OAuthStateStorePort {

    /** state→provider+redirectUri 매핑을 TTL과 함께 저장한다. */
    void save(String state, AuthProvider provider, String redirectUri, Duration ttl);

    /** state를 1회 소비한다. provider와 redirectUri가 모두 일치하면 true, 아니면 false. */
    boolean consume(String state, AuthProvider provider, String redirectUri);
}
