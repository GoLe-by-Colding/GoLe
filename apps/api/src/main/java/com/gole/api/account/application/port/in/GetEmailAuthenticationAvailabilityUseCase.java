package com.gole.api.account.application.port.in;

/** Inbound port: 이메일 인증 발송 수단(실제 메일 또는 명시적인 개발용 로그 전달)이 있는가. 공개 설정 화면이 이것을 본다. */
public interface GetEmailAuthenticationAvailabilityUseCase {

    boolean available();
}
