package com.gole.api.notification.adapter.out.logging;

import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 알림톡 발송 어댑터(스텁). CoolSMS가 꺼져 있을 때(로컬 개발·CI) 대신 등록된다.
 *
 * <p>{@code account} 컨텍스트의 {@code LoggingVerificationCodeSenderAdapter}와 같은 이유다 —
 * 발송 포트가 아예 없으면 전화번호 인증(D2/D3)을 로컬에서 끝까지 밟아볼 수 없고, 게이팅
 * E2E 테스트도 실제 카카오 알림톡 승인 전에는 작성할 수 없다. 변수 맵에 인증코드가 그대로
 * 들어 있으므로 로그를 프로덕션에 남기지 않도록 CoolSMS 어댑터와 상호 배타로 등록한다.
 */
@Component
@ConditionalOnProperty(name = "coolsms.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingAlimtalkSenderAdapter implements AlimtalkSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlimtalkSenderAdapter.class);

    @Override
    public AlimtalkAcceptance send(SendAlimtalkCommand command) {
        // 로컬 개발에서만 사용하는 전달 수단이다. 운영은 CoolSMS 어댑터를 활성화해 이 로그를
        // 남기지 않는다.
        log.info(
                "[ALIMTALK:LOCAL_ONLY] to={} templateId={} variables={}",
                mask(command.to()),
                command.templateId(),
                command.variables());
        return new AlimtalkAcceptance("local-only", "local-only", "LOCAL_ONLY", "발송하지 않고 로그만 남김");
    }

    private static String mask(String phoneNumber) {
        return phoneNumber.length() <= 4 ? "***" : phoneNumber.substring(0, phoneNumber.length() - 4) + "****";
    }
}
