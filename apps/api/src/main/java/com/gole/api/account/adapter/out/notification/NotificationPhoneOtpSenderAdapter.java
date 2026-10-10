package com.gole.api.account.adapter.out.notification;

import com.gole.api.account.application.port.out.PhoneOtpSenderPort;
import com.gole.api.notification.application.port.in.SendAlimtalkUseCase;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 알림 컨텍스트 통합 어댑터. 전화 인증 코드를 notification 의 알림톡 발송 유스케이스로 보낸다. */
@Component
public class NotificationPhoneOtpSenderAdapter implements PhoneOtpSenderPort {

    private final SendAlimtalkUseCase alimtalk;

    public NotificationPhoneOtpSenderAdapter(SendAlimtalkUseCase alimtalk) {
        this.alimtalk = alimtalk;
    }

    @Override
    public boolean send(String phoneNumber, String templateId, String codeVariable, String code) {
        return alimtalk.send(phoneNumber, templateId, Map.of(codeVariable, code));
    }
}
