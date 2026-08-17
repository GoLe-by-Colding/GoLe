package com.gole.api.notification.application.port.out;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Outbound port: 승인된 카카오 알림톡 템플릿의 단건 발송. */
public interface AlimtalkSenderPort {

    AlimtalkAcceptance send(SendAlimtalkCommand command);

    /**
     * @param to 국내 휴대폰 번호
     * @param templateId CoolSMS 알림톡 템플릿 연동 ID
     * @param variables 템플릿 변수. 키는 승인 템플릿 표기 그대로 전달한다.
     */
    record SendAlimtalkCommand(String to, String templateId, Map<String, String> variables) {

        public SendAlimtalkCommand {
            variables = variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        }
    }

    /** CoolSMS가 메시지를 정상 접수한 결과. 최종 단말 수신 성공을 의미하지 않는다. */
    record AlimtalkAcceptance(String groupId, String messageId, String statusCode, String statusMessage) {}
}
