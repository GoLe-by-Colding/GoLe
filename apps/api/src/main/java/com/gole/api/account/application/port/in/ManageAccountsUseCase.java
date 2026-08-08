package com.gole.api.account.application.port.in;

import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Role;
import java.time.Instant;
import java.util.List;

/**
 * Inbound port: 운영자의 회원 관리(조회/정지/복구/권한). (admin-console 요구사항 6)
 *
 * <p>관리자 컨텍스트는 이 포트만 알며, 계정 컬렉션이나 세션 저장소를 직접 다루지 않는다.
 * 정지·권한 변경에 따른 세션 폐기도 이 포트의 구현이 책임진다.
 */
public interface ManageAccountsUseCase {

    /** 최근 가입순 목록. {@code emailQuery}가 있으면 이메일 부분 일치로 좁힌다. (6.1) */
    List<AccountSummary> list(String emailQuery, int limit);

    /** 계정 정지 + 활성 세션 전량 폐기. (6.2, 6.3) */
    AccountSummary suspend(String accountId, String actorAccountId, String reason);

    /** 정지 해제. 실패 카운터/잠금도 함께 초기화된다. (6.6) */
    AccountSummary reinstate(String accountId, String actorAccountId);

    /** 권한 변경 + 세션 폐기(새 권한으로 재로그인 유도). (6.7) */
    AccountSummary changeRole(String accountId, String actorAccountId, Role newRole);

    /** 운영 화면용 계정 요약(read model). 비밀번호 해시 등 민감 정보는 포함하지 않는다. */
    record AccountSummary(
            String id, String email, Role role, AccountStatus status, Instant lockedUntil, String suspendedReason) {}
}
