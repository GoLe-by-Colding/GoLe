package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.VerificationCode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 계정 영속성 어댑터. 도메인 {@link Account}와 {@link AccountDocument}를 양방향 매핑한다.
 */
@Component
public class AccountPersistenceAdapter implements AccountRepositoryPort {

    private final AccountMongoRepository repository;

    public AccountPersistenceAdapter(AccountMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public Account save(Account account) {
        AccountDocument saved = repository.save(toDocument(account));
        return toDomain(saved);
    }

    @Override
    public Optional<Account> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(this::toDomain);
    }

    private AccountDocument toDocument(Account account) {
        VerificationCode code = account.getVerificationCode();
        return new AccountDocument(
                account.getId(),
                account.getEmail().value(),
                account.getPasswordHash().value(),
                account.getStatus().name(),
                account.getRole().name(),
                code == null ? null : code.code(),
                code == null ? null : code.issuedAt(),
                account.getFailedAttempts(),
                account.getFailureWindowStartedAt(),
                account.getLockedUntil());
    }

    private Account toDomain(AccountDocument document) {
        VerificationCode code =
                document.getVerificationCode() == null
                        ? null
                        : new VerificationCode(
                                document.getVerificationCode(),
                                document.getVerificationCodeIssuedAt());
        return new Account(
                document.getId(),
                new Email(document.getEmail()),
                new PasswordHash(document.getPasswordHash()),
                AccountStatus.valueOf(document.getStatus()),
                document.getRole() == null ? Role.USER : Role.valueOf(document.getRole()),
                code,
                document.getFailedAttempts(),
                document.getFailureWindowStartedAt(),
                document.getLockedUntil());
    }
}
