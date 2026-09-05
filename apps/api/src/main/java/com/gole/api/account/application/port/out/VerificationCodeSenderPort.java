package com.gole.api.account.application.port.out;

import com.gole.api.account.domain.model.Email;
import com.gole.api.account.domain.model.VerificationCode;

/**
 * 이메일 인증 코드 발송 outbound port. (요구사항 1.1)
 */
public interface VerificationCodeSenderPort {

    void send(Email email, VerificationCode code);

    /**
     * 비밀번호 재설정 코드를 보낸다. 기존 구현·테스트 람다와의 호환을 위해 기본 구현은 일반 인증 발송으로
     * 위임하지만, 사용자에게 보이는 어댑터는 목적에 맞는 제목과 본문으로 재정의한다.
     */
    default void sendPasswordReset(Email email, VerificationCode code) {
        send(email, code);
    }

    /** 회원 탈퇴 본인확인 코드를 목적이 구분된 메일로 보낸다. */
    default void sendAccountDeletion(Email email, VerificationCode code) {
        send(email, code);
    }
}
