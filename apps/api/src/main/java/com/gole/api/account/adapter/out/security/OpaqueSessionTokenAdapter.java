package com.gole.api.account.adapter.out.security;

import com.gole.api.account.application.port.out.SessionTokenPort;
import com.gole.api.account.domain.model.Account;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 불투명(opaque) 세션 토큰 발급 어댑터. (요구사항 1.6)
 * 256비트 보안 난수를 URL-safe Base64로 인코딩한 토큰을 발급한다.
 */
@Component
public class OpaqueSessionTokenAdapter implements SessionTokenPort {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public String issue(Account account) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
