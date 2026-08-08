package com.gole.api.account.adapter.out.verification;

import com.gole.api.account.application.port.out.VerificationCodeSenderPort;
import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 인증 코드 발송 어댑터(스텁). 현재는 코드를 로그로만 출력한다.
 */
@Component
@ConditionalOnProperty(name = "gole.verification.email.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingVerificationCodeSenderAdapter implements VerificationCodeSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationCodeSenderAdapter.class);

    @Override
    public void send(Email email, VerificationCode code) {
        // 로컬 개발에서만 사용하는 전달 수단이다. 운영은 SMTP 어댑터를 활성화해 코드 로그를 남기지 않는다.
        log.info("[VERIFICATION:LOCAL_ONLY] to={} code={}", mask(email.value()), code.code());
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(0, at)) : email.charAt(0) + "***" + email.substring(at);
    }
}
