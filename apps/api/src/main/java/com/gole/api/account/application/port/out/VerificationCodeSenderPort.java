package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;

/**
 * 이메일 인증 코드 발송 outbound port. (요구사항 1.1)
 */
public interface VerificationCodeSenderPort {

    void send(Email email, VerificationCode code);
}
