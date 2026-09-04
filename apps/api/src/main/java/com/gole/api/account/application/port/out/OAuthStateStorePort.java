package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.AuthProvider;
import com.gole.api.account.domain.model.SignupPolicyAcceptance;
import java.time.Duration;
import java.util.Optional;

/**
 * Outbound port: OAuth state(CSRF 방지) 저장소. 서버가 state를 발급·저장하고 콜백에서 1회 소비한다.
 * (소셜 로그인 보안 강화 — 서버측 state 검증)
 */
public interface OAuthStateStorePort {

    /** state→요청 문맥 매핑을 TTL과 함께 저장한다. 신규 가입 확인 정보와 복귀 경로도 여기 결박한다. */
    void save(String state, OAuthStateContext context, Duration ttl);

    /** state를 1회 소비한다. 알 수 없거나 이미 소비된 state면 empty다. */
    Optional<OAuthStateContext> consume(String state);

    record OAuthStateContext(
            AuthProvider provider, String redirectUri, SignupPolicyAcceptance signupPolicyAcceptance, String returnTo) {

        public OAuthStateContext(
                AuthProvider provider, String redirectUri, SignupPolicyAcceptance signupPolicyAcceptance) {
            this(provider, redirectUri, signupPolicyAcceptance, null);
        }
    }
}
