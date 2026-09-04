package com.gole.api.notification.adapter.out.logging;

import com.gole.api.notification.application.port.out.AlimtalkSendException;
import com.gole.api.notification.application.port.out.AlimtalkSendException.FailureType;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 알림톡 발송 어댑터(스텁). CoolSMS가 꺼져 있을 때(로컬 개발·CI) 대신 등록된다.
 *
 * <p>{@code account} 컨텍스트의 {@code LoggingVerificationCodeSenderAdapter}와 같은 이유다 —
 * 발송 포트가 아예 없으면 전화번호 인증(D2/D3)을 로컬에서 끝까지 밟아볼 수 없고, 게이팅
 * E2E 테스트도 실제 카카오 알림톡 승인 전에는 작성할 수 없다. 공개 환경은 호출 자체를
 * 실패로 닫고, 로컬에서도 민감값은 명시적으로 옵트인하지 않으면 가려서 기록한다.
 */
@Component
@ConditionalOnProperty(name = "coolsms.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingAlimtalkSenderAdapter implements AlimtalkSenderPort {

    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("local", "dev", "development", "test", "e2e");
    private static final Logger log = LoggerFactory.getLogger(LoggingAlimtalkSenderAdapter.class);
    private final String environment;
    private final boolean logVerificationCodes;

    public LoggingAlimtalkSenderAdapter(
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.onboarding.log-verification-codes:false}") boolean logVerificationCodes) {
        this.environment = environment == null ? "" : environment.trim().toLowerCase(Locale.ROOT);
        this.logVerificationCodes = logVerificationCodes;
    }

    @Override
    public AlimtalkAcceptance send(SendAlimtalkCommand command) {
        if (!ALLOWED_ENVIRONMENTS.contains(environment)) {
            // 전화 인증이 선택인 초기 운영에서도 엔드포인트를 직접 호출할 수 있다. 그 요청을
            // 성공처럼 처리하면 사용자는 전송되지 않은 코드를 기다리므로 공개 환경은 무조건
            // 실패로 닫는다. 예외에는 수신자·코드·템플릿 값을 넣지 않는다.
            throw new AlimtalkSendException(
                    FailureType.PROVIDER_FAILURE, "Logging alimtalk adapter is disabled outside local environments");
        }

        if (logVerificationCodes) {
            // 민감 코드 출력은 로컬 계열 환경 + 명시적 옵트인 두 조건을 모두 만족할 때만 허용한다.
            log.info(
                    "[ALIMTALK:LOCAL_ONLY] to={} templateId={} variables={}",
                    mask(command.to()),
                    command.templateId(),
                    command.variables());
        } else {
            // 기본 로그에는 OTP 원문을 남기지 않는다. 변수명만으로 템플릿 조립 여부를 확인한다.
            log.info(
                    "[ALIMTALK:LOCAL_ONLY] to={} templateId={} variableNames={} sensitiveValues=redacted",
                    mask(command.to()),
                    command.templateId(),
                    command.variables().keySet());
        }
        return new AlimtalkAcceptance("local-only", "local-only", "LOCAL_ONLY", "발송하지 않고 로그만 남김");
    }

    private static String mask(String phoneNumber) {
        return phoneNumber.length() <= 4 ? "***" : phoneNumber.substring(0, phoneNumber.length() - 4) + "****";
    }
}
