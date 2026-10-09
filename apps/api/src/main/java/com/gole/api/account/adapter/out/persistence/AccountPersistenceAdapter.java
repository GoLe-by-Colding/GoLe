package com.gole.api.account.adapter.out.persistence;

import com.gole.api.account.application.port.in.ListInterestTagRecipientsUseCase.MarketingRecipient;
import com.gole.api.account.application.port.out.AccountRepositoryPort;
import com.gole.api.account.domain.exception.EmailAlreadyRegisteredException;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.AccountStatus;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.EmailVerificationChallenge;
import com.gole.api.account.domain.model.Nickname;
import com.gole.api.account.domain.model.OnboardingProfile;
import com.gole.api.account.domain.model.PasswordHash;
import com.gole.api.account.domain.model.PhoneNumber;
import com.gole.api.account.domain.model.Role;
import com.gole.api.common.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AccountPersistenceAdapter.class);

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
    public List<String> findMarketingReachableIdsByInterestTag(String tagKey, String afterAccountId, int limit) {
        Criteria criteria = eligibleMarketingRecipient(tagKey);
        if (afterAccountId != null && !afterAccountId.isBlank()) {
            criteria =
                    new Criteria().andOperator(criteria, Criteria.where("_id").gt(afterAccountId));
        }
        Query query =
                new Query(criteria).with(Sort.by(Sort.Direction.ASC, "_id")).limit(Math.max(1, limit));
        query.fields().include("_id");
        return mongoTemplate.find(query, AccountDocument.class).stream()
                .map(AccountDocument::getId)
                .toList();
    }

    @Override
    public Optional<MarketingRecipient> findMarketingRecipient(String accountId, String tagKey) {
        Query query = new Query(
                new Criteria().andOperator(Criteria.where("_id").is(accountId), eligibleMarketingRecipient(tagKey)));
        return Optional.ofNullable(mongoTemplate.findOne(query, AccountDocument.class))
                .flatMap(document -> Optional.ofNullable(PhoneNumber.ofNullable(document.getPhoneNumber()))
                        .map(phoneNumber -> new MarketingRecipient(document.getId(), phoneNumber)));
    }

    @Override
    public void fenceAdminMutation() {
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is("admin-role-fence")),
                new Update().inc("version", 1),
                "account_admin_fences");
    }

    private Criteria eligibleMarketingRecipient(String tagKey) {
        return new Criteria()
                .andOperator(
                        Criteria.where("interestTags").is(tagKey),
                        Criteria.where("phoneVerifiedAt").ne(null),
                        Criteria.where("marketingConsentedAt").ne(null),
                        Criteria.where("status").is(AccountStatus.VERIFIED.name()));
    }

    private AccountDocument toDocument(Account account) {
        EmailVerificationChallenge challenge = account.getVerificationChallenge();
        OnboardingProfile onboarding = account.getOnboarding();
        Nickname nickname = onboarding.nickname();
        PhoneNumber phoneNumber = onboarding.phoneNumber();
        return new AccountDocument(
                account.getId(),
                account.getEmail().value(),
                account.getPasswordHash().value(),
                account.getStatus().name(),
                account.getRole().name(),
                challenge == null ? null : challenge.codeHash(),
                challenge == null ? null : challenge.issuedAt(),
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
        EmailVerificationChallenge challenge = verificationChallenge(document);
        return new Account(
                document.getId(),
                new Email(document.getEmail()),
                new PasswordHash(document.getPasswordHash()),
                AccountStatus.valueOf(document.getStatus()),
                document.getRole() == null ? Role.USER : Role.valueOf(document.getRole()),
                challenge,
                document.getVerificationFailedAttempts(),
                document.getFailedAttempts(),
                document.getFailureWindowStartedAt(),
                document.getLockedUntil(),
                document.getSuspendedReason(),
                toOnboardingProfile(document));
    }

    /**
     * 인증 코드 hash와 발급 시각이 둘 다 있을 때만 대기 중인 이메일 인증으로 읽는다.
     *
     * <p>한쪽만 남은 문서(수동 정리·옛 필드명 {@code verificationCode}로 $unset 등)를 그대로 challenge로 만들면
     * 생성자 검증이 던져 이 계정을 읽는 모든 경로(로그인·관리자 회원 목록)가 500으로 죽는다. 손상된 대기 상태는
     * "발급된 코드 없음"으로 읽는다 — 미인증 계정이면 인증 시 {@code VERIFICATION_CODE_MISSING}이 나고 새 코드를
     * 받으면 된다. 다음 저장에서 두 필드가 함께 비워져 문서도 정리된다.
     */
    private static EmailVerificationChallenge verificationChallenge(AccountDocument document) {
        String hash = document.getVerificationCodeHash();
        Instant issuedAt = document.getVerificationCodeIssuedAt();
        if (hash != null && !hash.isBlank() && issuedAt != null) {
            return new EmailVerificationChallenge(hash, issuedAt);
        }
        if (hash != null || issuedAt != null) {
            // 값은 남기지 않는다 — 어느 계정이 손상됐는지만 운영에서 찾을 수 있으면 된다.
            log.warn("[account] 인증 코드 상태가 반쪽인 문서를 대기 인증 없음으로 읽음: accountId={}", document.getId());
        }
        return null;
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
