package com.gole.api.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.account.domain.model.PasswordHash;
import org.junit.jupiter.api.Test;

/**
 * BCrypt 해셔 어댑터 단위 테스트. (요구사항 1.9, 1.12)
 * 프레임워크 컨텍스트 없이 어댑터를 직접 생성해 검증한다.
 */
class BCryptPasswordHasherAdapterTest {

    private final Sha256PasswordHasherAdapter legacy = new Sha256PasswordHasherAdapter();
    private final BCryptPasswordHasherAdapter hasher = new BCryptPasswordHasherAdapter(legacy);

    @Test
    void hash_producesBcryptFormat_andMatches() {
        PasswordHash hash = hasher.hash("password1");

        assertThat(hash.value()).startsWith("$2");
        assertThat(hasher.matches("password1", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    void freshBcryptHash_doesNotNeedRehash() {
        PasswordHash hash = hasher.hash("password1");

        assertThat(hasher.needsRehash(hash)).isFalse();
    }

    @Test
    void verifiesLegacySha256Hash_andFlagsForRehash() {
        // 기존 계정이 SHA-256 포맷으로 저장된 상황을 재현한다.
        PasswordHash legacyHash = legacy.hash("password1");

        assertThat(legacyHash.value()).startsWith("sha256$");
        // 레거시 해시도 정상 검증되어 기존 사용자가 로그인할 수 있어야 한다.
        assertThat(hasher.matches("password1", legacyHash)).isTrue();
        assertThat(hasher.matches("wrong", legacyHash)).isFalse();
        // 레거시 해시는 BCrypt로 승격 대상이어야 한다.
        assertThat(hasher.needsRehash(legacyHash)).isTrue();
    }
}
