package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.Nickname;
import com.gole.api.account.domain.model.PhoneNumber;
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

    /**
     * 대소문자를 무시한 닉네임 중복 확인. (onboarding R3, D9)
     *
     * @param excludingAccountId 자기 자신은 제외한다(같은 닉네임 재설정이 중복으로 걸리지 않게)
     */
    boolean existsByNickname(Nickname nickname, String excludingAccountId);

    /**
     * <b>인증까지 끝난</b> 같은 번호를 가진 다른 계정이 있는가. (onboarding R4, D4)
     *
     * <p>입력만 하고 인증하지 않은 번호는 점유로 치지 않는다 — 그렇지 않으면 남의 번호를
     * 입력해 두는 것만으로 그 번호를 영구히 막을 수 있다.
     */
    boolean existsByVerifiedPhoneNumber(PhoneNumber phoneNumber, String excludingAccountId);

    /** 관리자 정지·강등 판단을 다중 인스턴스에서도 직렬화하는 영속성 fence. */
    default void fenceAdminMutation() {}
}
