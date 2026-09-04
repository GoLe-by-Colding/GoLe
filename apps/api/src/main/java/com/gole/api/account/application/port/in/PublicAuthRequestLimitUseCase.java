package com.gole.api.account.application.port.in;

/**
 * 인증 전 공개 엔드포인트의 남용 방지 한도를 획득한다.
 *
 * <p>이메일 재발송·비밀번호 재설정 요청은 같은 수신자 쿨다운 안에서 계정 존재 여부와 무관하게
 * 조용히 성공 응답을 반환해야 하므로, 실제 유스케이스를 계속 실행할지 boolean으로 알린다.
 */
public interface PublicAuthRequestLimitUseCase {

    void acquireRegistration(String email, String clientAddress);

    boolean acquireVerificationResend(String email, String clientAddress);

    boolean acquirePasswordReset(String email, String clientAddress);

    void acquireOAuthAuthorization(String clientAddress);
}
