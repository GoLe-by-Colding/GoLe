package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.VerificationCode;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        try {
            AccountDocument saved = repository.save(toDocument(account));
            return toDomain(saved);
        } catch (DuplicateKeyException ex) {
            throw new EmailAlreadyRegisteredException(account.getEmail().value());
        }
    }

    @Override
    public Optional<Account> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(this::toDomain);
    }

    @Override
    public Optional<Account> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Account> findRecent(String emailQuery, int limit) {
        Pageable page = PageRequest.of(0, Math.max(1, limit), Sort.by(Sort.Direction.DESC, "_id"));
        List<AccountDocument> rows = emailQuery == null || emailQuery.isBlank()
                ? repository.findBy(page)
                : repository.findByEmailContainingIgnoreCase(emailQuery.trim(), page);
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRole(Role role) {
        return repository.countByRole(role.name());
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
                account.getVerificationFailedAttempts(),
                account.getFailedAttempts(),
                account.getFailureWindowStartedAt(),
                account.getLockedUntil(),
                account.getSuspendedReason());
    }

    private Account toDomain(AccountDocument document) {
        VerificationCode code = document.getVerificationCode() == null
                ? null
                : new VerificationCode(document.getVerificationCode(), document.getVerificationCodeIssuedAt());
        return new Account(
                document.getId(),
                new Email(document.getEmail()),
                new PasswordHash(document.getPasswordHash()),
                AccountStatus.valueOf(document.getStatus()),
                document.getRole() == null ? Role.USER : Role.valueOf(document.getRole()),
                code,
                document.getVerificationFailedAttempts(),
                document.getFailedAttempts(),
                document.getFailureWindowStartedAt(),
                document.getLockedUntil(),
                document.getSuspendedReason());
    }
}
