package com.gole.api.notification.application.service;

import com.gole.api.notification.application.port.in.SendAlimtalkUseCase;
import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.SendAlimtalkCommand;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 알림톡 단건 발송. 실제 CoolSMS 빈이나 로컬 전용 로깅 빈이 없는 구성도 부팅할 수 있도록 발송 어댑터를 {@link Optional}로 받는다. 공개
 * 환경의 로깅 빈은 요청을 성공처럼 처리하지 않고 실패로 닫는다.
 */
@Service
public class AlimtalkDispatchService implements SendAlimtalkUseCase {

    private final Optional<AlimtalkSenderPort> sender;

    public AlimtalkDispatchService(Optional<AlimtalkSenderPort> sender) {
        this.sender = sender;
    }

    @Override
    public boolean send(String to, String templateId, Map<String, String> variables) {
        if (sender.isEmpty()) {
            return false;
        }
        try {
            sender.get().send(new SendAlimtalkCommand(to, templateId, variables));
            return true;
        } catch (AlimtalkSendException rejected) {
            return false;
        }
    }
}
