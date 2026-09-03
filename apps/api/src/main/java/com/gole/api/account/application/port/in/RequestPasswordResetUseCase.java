package com.gole.api.account.application.port.in;

/** 계정 존재 여부를 노출하지 않고 비밀번호 재설정 코드를 요청한다. */
public interface RequestPasswordResetUseCase {

    void request(RequestPasswordResetCommand command);

    record RequestPasswordResetCommand(String email) {}
}
