package com.gole.api.account.adapter.out.verification;

import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 인증 코드 발송 어댑터(스텁). 현재는 코드를 로그로만 출력한다.
 */
@Component
public class LoggingVerificationCodeSenderAdapter implements VerificationCodeSenderPort {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingVerificationCodeSenderAdapter.class);

    @Override
    public void send(Email email, VerificationCode code) {
        // TODO: integrate real email/SMS
        log.info("[VERIFICATION] to={} code={}", email.value(), code.code());
    }
}
