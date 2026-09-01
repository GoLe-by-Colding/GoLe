package com.gole.api.account.application.port.in;

/**
 * Inbound port: 약관 동의 제출. (onboarding R7)
 */
public interface SubmitOnboardingConsentUseCase {

    void submit(SubmitConsentCommand command);

    /**
     * @param privacyAgreed 개인정보 수집·이용 동의(필수). false면 거부한다.
     * @param marketingAgreed 마케팅 수신 동의(선택)
     */
    record SubmitConsentCommand(String accountId, boolean privacyAgreed, boolean marketingAgreed) {}
}
