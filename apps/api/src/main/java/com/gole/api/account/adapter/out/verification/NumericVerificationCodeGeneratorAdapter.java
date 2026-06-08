package com.gole.api.account.adapter.out.verification;

import com.gole.api.account.application.port.out.VerificationCodeGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 6자리 숫자 인증 코드 생성 어댑터. (요구사항 1.1)
 */
@Component
public class NumericVerificationCodeGeneratorAdapter implements VerificationCodeGeneratorPort {

    private static final int BOUND = 1_000_000; // 000000 ~ 999999

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateCode() {
        return String.format("%06d", secureRandom.nextInt(BOUND));
    }
}
