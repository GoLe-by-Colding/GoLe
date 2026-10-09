package com.gole.api.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.domain.model.Account;
import com.gole.api.account.domain.model.EmailVerificationChallenge;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class AccountPersistenceAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-10T01:00:00Z");

    private final AccountMongoRepository repository = mock(AccountMongoRepository.class);
    private final AccountPersistenceAdapter adapter =
            new AccountPersistenceAdapter(repository, mock(MongoTemplate.class));

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
}
