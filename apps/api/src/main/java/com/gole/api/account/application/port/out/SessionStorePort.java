package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 세션 저장소 outbound port. 발급된 세션 토큰을 계정/권한과 매핑해 저장하고 해석한다.
 * 이를 통해 불투명 토큰이 실제로 검증 가능한 세션이 된다(스테이트풀 세션).
 */
public interface SessionStorePort {

    /** 토큰→(계정,권한) 매핑을 TTL과 함께 저장한다. */
    void store(String token, String accountId, Role role, Duration ttl);

    /** 최초 발급·직전 회전 시각을 함께 저장한다. 레거시 구현은 기존 저장 계약으로 폴백한다. */
    default void store(String token, String accountId, Role role, Instant issuedAt, Instant rotatedAt, Duration ttl) {
        store(token, accountId, role, ttl);
    }

    /** 토큰을 해석한다. 없거나 만료면 비어있음. */
    Optional<SessionPrincipal> resolve(String token);

    /** 활성 요청의 유휴 만료 시간을 연장한다. 레거시 구현에서는 안전하게 아무 일도 하지 않는다. */
    default void touch(String token, Duration ttl) {}

    /** 계정 인덱스까지 함께 연장할 수 있는 확장 계약. 기존 구현은 토큰 TTL 갱신으로 폴백한다. */
    default void touch(String token, String accountId, Duration ttl) {
        touch(token, ttl);
    }

    /** 토큰을 폐기한다(로그아웃). */
    void revoke(String token);

    /**
     * 해당 계정의 모든 활성 세션을 폐기한다. (admin-console 요구사항 6.3, 6.7)
     *
     * <p>정지·권한 변경이 이미 발급된 토큰에도 즉시 반영되게 한다.
     */
    void revokeAllForAccount(String accountId);

    /** 인증된 호출자 식별 정보. */
    record SessionPrincipal(String accountId, Role role, Instant issuedAt, Instant rotatedAt) {
        /** 기존 테스트·어댑터와의 호환용 생성자. 메타데이터가 없는 세션은 다음 갱신 때 v2로 승격된다. */
        public SessionPrincipal(String accountId, Role role) {
            this(accountId, role, null, null);
        }
    }
}
