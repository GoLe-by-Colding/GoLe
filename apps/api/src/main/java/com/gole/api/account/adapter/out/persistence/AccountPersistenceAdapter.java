package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.Nickname;
import com.gole.api.account.domain.model.OnboardingProfile;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.PhoneNumber;
import com.gole.api.account.domain.model.Role;
import com.gole.api.account.domain.model.VerificationCode;
import com.gole.api.common.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * 계정 영속성 어댑터. 도메인 {@link Account}와 {@link AccountDocument}를 양방향 매핑한다.
 */
@Component
public class AccountPersistenceAdapter implements AccountRepositoryPort {

    private final AccountMongoRepository repository;
    private final MongoTemplate mongoTemplate;

    public AccountPersistenceAdapter(AccountMongoRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
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
            // 유일 인덱스가 email 하나뿐이던 시절의 가정이 깨졌다. 닉네임 충돌을
            // "이메일 중복"으로 보고하면 사용자가 영문 모를 안내를 받는다.
            if (String.valueOf(ex.getMessage()).contains("nicknameNormalized")) {
                throw new ConflictException("NICKNAME_ALREADY_IN_USE", "이미 사용 중인 닉네임입니다");
            }
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

    @Override
    public boolean existsByNickname(Nickname nickname, String excludingAccountId) {
        return repository
                .findByNicknameNormalized(nickname.normalized())
                .filter(other -> !other.getId().equals(excludingAccountId))
                .isPresent();
    }

    @Override
    public boolean existsByVerifiedPhoneNumber(PhoneNumber phoneNumber, String excludingAccountId) {
        return repository
                .findByPhoneNumberAndPhoneVerifiedAtNotNull(phoneNumber.value())
                .filter(other -> !other.getId().equals(excludingAccountId))
                .isPresent();
    }

    @Override
    public void fenceAdminMutation() {
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is("admin-role-fence")),
                new Update().inc("version", 1),
                "account_admin_fences");
    }

    private AccountDocument toDocument(Account account) {
        VerificationCode code = account.getVerificationCode();
        OnboardingProfile onboarding = account.getOnboarding();
        Nickname nickname = onboarding.nickname();
        PhoneNumber phoneNumber = onboarding.phoneNumber();
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
                account.getSuspendedReason(),
                nickname == null ? null : nickname.value(),
                nickname == null ? null : nickname.normalized(),
                phoneNumber == null ? null : phoneNumber.value(),
                onboarding.phoneVerifiedAt(),
                // 빈 Set을 그대로 넣으면 sparse 인덱스·부분 조회에서 "값 있음"으로 취급된다.
                onboarding.interestTags().isEmpty() ? null : Set.copyOf(onboarding.interestTags()),
                onboarding.privacyConsentedAt(),
                onboarding.marketingConsentedAt(),
                onboarding.legacyExempt());
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
                document.getSuspendedReason(),
                toOnboardingProfile(document));
    }

    private OnboardingProfile toOnboardingProfile(AccountDocument document) {
        return new OnboardingProfile(
                Nickname.ofNullable(document.getNickname()),
                PhoneNumber.ofNullable(document.getPhoneNumber()),
                document.getPhoneVerifiedAt(),
                document.getInterestTags(),
                document.getPrivacyConsentedAt(),
                document.getMarketingConsentedAt(),
                document.isLegacyExempt());
    }
}
