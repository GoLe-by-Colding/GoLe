package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.Email;
import java.util.Optional;

/**
 * 계정 영속성 outbound port. 도메인은 저장 기술(MongoDB 등)에 의존하지 않는다.
 */
public interface AccountRepositoryPort {

    boolean existsByEmail(Email email);

    Account save(Account account);

    Optional<Account> findByEmail(Email email);
}
