package com.gole.api.account.application.port.in;

/**
 * Inbound port: 온보딩 닉네임 설정. (onboarding R3, D9)
 */
public interface SetNicknameUseCase {

    void setNickname(SetNicknameCommand command);

    record SetNicknameCommand(String accountId, String nickname) {}
}
