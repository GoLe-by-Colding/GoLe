package com.gole.api.account.application.port.in;

/**
 * Inbound port: 로그아웃. 서버측 세션(불투명 토큰)을 폐기한다. (DELETE /api/v1/accounts/sessions)
 */
public interface LogoutUseCase {

    /** 토큰에 해당하는 세션을 폐기한다. 멱등(없는 토큰이면 무시). */
    void logout(String sessionToken);
}
