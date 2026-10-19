package com.gole.api.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.EmailVerificationChallenge;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class AccountPersistenceAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-10T01:00:00Z");

    private final AccountMongoRepository repository = mock(AccountMongoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final AccountPersistenceAdapter adapter = new AccountPersistenceAdapter(repository, mongo);

    @Test
    @DisplayName("이전 소셜 가입 계정은 최초 정책 동의 채널에서 비밀번호 자격 없음으로 복원한다")
    void findById_restoresLegacySocialPasswordState() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document(null, null)));
        when(mongo.findOne(any(Query.class), eq(PolicyAcceptanceDocument.class)))
                .thenReturn(new PolicyAcceptanceDocument(
                        "policy-1", "account-1", "v1", "v1", true, true, true, "SOCIAL_GOOGLE", ISSUED_AT));

        assertThat(adapter.findById("account-1").orElseThrow().isPasswordLoginEnabled())
                .isFalse();
    }

    @Test
    @DisplayName("이전 이메일 가입 계정은 소셜 로그인을 병행해도 비밀번호 자격을 유지한다")
    void findById_restoresLegacyEmailPasswordState() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document(null, null)));
        when(mongo.findOne(any(Query.class), eq(PolicyAcceptanceDocument.class)))
                .thenReturn(new PolicyAcceptanceDocument(
                        "policy-1", "account-1", "v1", "v1", true, true, true, "EMAIL", ISSUED_AT));

        assertThat(adapter.findById("account-1").orElseThrow().isPasswordLoginEnabled())
                .isTrue();
    }

    @Test
    @DisplayName("새 계정의 명시적인 비밀번호 상태는 가입 이력보다 우선한다")
    void findById_prefersExplicitPasswordState() {
        when(repository.findById("account-1")).thenReturn(Optional.of(documentWithPasswordState(false)));

        assertThat(adapter.findById("account-1").orElseThrow().isPasswordLoginEnabled())
                .isFalse();
        verifyNoInteractions(mongo);
    }

    @Test
    @DisplayName("인증 코드 hash만 있고 발급 시각이 없는 문서도 500 없이 '발급된 코드 없음'으로 읽는다")
    void findById_readsHashWithoutIssuedAtAsNoPendingVerification() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document("code-hash", null)));

        Account account = adapter.findById("account-1").orElseThrow();

        assertThat(account.getVerificationChallenge()).isNull();
    }

    @Test
    @DisplayName("발급 시각만 남은 문서도 대기 인증 없음으로 읽는다")
    void findById_readsIssuedAtWithoutHashAsNoPendingVerification() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document(null, ISSUED_AT)));

        assertThat(adapter.findById("account-1").orElseThrow().getVerificationChallenge())
                .isNull();
    }

    @Test
    @DisplayName("빈 hash는 대기 인증으로 만들지 않는다")
    void findById_readsBlankHashAsNoPendingVerification() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document(" ", ISSUED_AT)));

        assertThat(adapter.findById("account-1").orElseThrow().getVerificationChallenge())
                .isNull();
    }

    @Test
    @DisplayName("hash와 발급 시각이 함께 있으면 대기 인증을 그대로 읽는다")
    void findById_keepsCompleteChallenge() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document("code-hash", ISSUED_AT)));

        assertThat(adapter.findById("account-1").orElseThrow().getVerificationChallenge())
                .isEqualTo(new EmailVerificationChallenge("code-hash", ISSUED_AT));
    }

    private static AccountDocument document(String verificationCodeHash, Instant verificationCodeIssuedAt) {
        return new AccountDocument(
                "account-1",
                "user@gole.test",
                "hashed-password",
                "UNVERIFIED",
                "USER",
                verificationCodeHash,
                verificationCodeIssuedAt,
                0,
                0,
                null,
                null,
                null);
    }

    private static AccountDocument documentWithPasswordState(boolean enabled) {
        return new AccountDocument(
                "account-1",
                "user@gole.test",
                "hashed-password",
                "VERIFIED",
                "USER",
                null,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                enabled);
    }
}
