package com.gole.api.account.application.port.in;

/** 로그인한 사용자의 비밀번호를 변경한다. 성공하면 해당 계정의 모든 세션을 폐기한다. */
public interface ChangePasswordUseCase {

    void change(ChangePasswordCommand command);

    record ChangePasswordCommand(String accountId, String currentPassword, String newPassword) {}
}
