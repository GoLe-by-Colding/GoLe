package com.gole.api.account.application.port.out;

/** Outbound port: 전화 인증 코드(OTP) 발송. 채널은 카카오 알림톡이다(onboarding D3). 이메일 인증은 {@link VerificationCodeSenderPort}. */
public interface PhoneOtpSenderPort {

    /**
     * @param templateId 승인된 알림톡 템플릿 ID
     * @param codeVariable 템플릿에서 코드가 들어갈 변수 이름
     * @return 발송 경로가 있고 사업자가 접수했으면 {@code true}
     */
    boolean send(String phoneNumber, String templateId, String codeVariable, String code);
}
