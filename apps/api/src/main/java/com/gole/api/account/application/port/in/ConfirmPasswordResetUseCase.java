package com.gole.api.account.application.port.in;

/** 이메일로 받은 일회용 코드로 비밀번호 재설정을 확정한다. */
public interface ConfirmPasswordResetUseCase {

    void confirm(ConfirmPasswordResetCommand command);

    record ConfirmPasswordResetCommand(String email, String code, String newPassword) {}
}
