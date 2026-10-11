package com.gole.api.notification.application.port.in;

import java.util.Map;

/** Inbound port: 승인된 카카오 알림톡 템플릿 단건 발송. 다른 컨텍스트(예: 전화 인증 OTP)가 발송 어댑터 대신 이것을 쓴다. */
public interface SendAlimtalkUseCase {

    /**
     * 발송 경로가 구성되지 않았거나 사업자가 접수하지 않으면 {@code false}. 접수(true)는 최종 단말 수신을 뜻하지 않는다.
     *
     * @param variables 템플릿 변수. 키는 승인 템플릿 표기 그대로 전달한다.
     */
    boolean send(String to, String templateId, Map<String, String> variables);
}
