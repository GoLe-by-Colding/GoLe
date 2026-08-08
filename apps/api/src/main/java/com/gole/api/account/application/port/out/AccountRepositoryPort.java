package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.Role;
import java.util.List;
import java.util.Optional;

/**
 * 계정 영속성 outbound port. 도메인은 저장 기술(MongoDB 등)에 의존하지 않는다.
 */
public interface AccountRepositoryPort {

    boolean existsByEmail(Email email);

    Account save(Account account);

    Optional<Account> findByEmail(Email email);

    /** 계정 ID로 조회. 세션 해석 시 이메일 등 프로필을 얻기 위해 사용한다. */
    Optional<Account> findById(String id);

    /**
     * 운영 화면용 최근 가입순 목록. (admin-console 요구사항 6.1)
     *
     * @param emailQuery 이메일 부분 일치 검색어. null/blank면 전체.
     */
    List<Account> findRecent(String emailQuery, int limit);

    /** 해당 권한을 가진 계정 수. 마지막 관리자 보호에 사용한다. (요구사항 6.9) */
    long countByRole(Role role);
}
