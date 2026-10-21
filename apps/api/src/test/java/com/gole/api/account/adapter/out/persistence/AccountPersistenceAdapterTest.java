package com.gole.api.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gole.api.account.adapter.out.security.BCryptPasswordHasherAdapter;
import com.gole.api.account.adapter.out.security.Sha256PasswordHasherAdapter;
import com.gole.api.account.application.port.in.SignInUseCase.SignInCommand;
import com.gole.api.account.application.port.out.SessionStorePort;
import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.application.service.AccountService;
import com.gole.api.account.application.service.OnboardingProperties;
import com.gole.api.account.application.service.PolicyAcceptanceService;
import com.gole.api.account.application.service.SessionPolicyProperties;
import com.gole.api.account.config.EmailAuthenticationAvailability;
import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.EmailVerificationChallenge;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class AccountPersistenceAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-10T01:00:00Z");

    private final AccountMongoRepository repository = mock(AccountMongoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final AccountPersistenceAdapter adapter = new AccountPersistenceAdapter(repository, mongo);

    @Test
    @DisplayName("이전 소셜 가입자가 설정한 비밀번호로 로그인하면 자격과 해시를 그대로 저장한다")
    void signIn_preservesLegacySocialPasswordAndPersistsEnabledState() {
        var hasher = new BCryptPasswordHasherAdapter(new Sha256PasswordHasherAdapter());
        String passwordHash = hasher.hash("previously-reset-password").value();
        AccountDocument legacy = new AccountDocument(
                "account-1", "user@gole.test", passwordHash, "VERIFIED", "USER", null, null, 0, 0, null, null, null);
        when(repository.findByEmail("user@gole.test")).thenReturn(Optional.of(legacy));
        when(repository.save(any(AccountDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.findOne(any(Query.class), eq(PolicyAcceptanceDocument.class)))
                .thenReturn(new PolicyAcceptanceDocument(
                        "policy-1", "account-1", "v1", "v1", true, true, true, "SOCIAL_GOOGLE", ISSUED_AT));
        AccountService service = new AccountService(
                adapter,
                hasher,
                mock(VerificationCodeSenderPort.class),
                () -> "123456",
                () -> "unused-account-id",
                account -> "session-token",
                mock(SessionStorePort.class),
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC),
                new SessionPolicyProperties(),
                mock(PolicyAcceptanceService.class),
                new OnboardingProperties(),
                new EmailAuthenticationAvailability("test", false));

        assertThat(service.signIn(new SignInCommand("user@gole.test", "previously-reset-password"))
                        .sessionToken())
                .isEqualTo("session-token");
        ArgumentCaptor<AccountDocument> saved = ArgumentCaptor.forClass(AccountDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPasswordLoginEnabled()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(passwordHash);
        verifyNoInteractions(mongo);
    }

    @Test
    @DisplayName("필드가 없는 이전 계정은 정책 이력 조회 없이 비밀번호 자격을 유지한다")
    void findById_restoresLegacyEmailPasswordState() {
        when(repository.findById("account-1")).thenReturn(Optional.of(document(null, null)));
        assertThat(adapter.findById("account-1").orElseThrow().isPasswordLoginEnabled())
                .isTrue();
        verifyNoInteractions(mongo);
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
