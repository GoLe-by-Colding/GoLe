package com.gole.api.chat.adapter.out.assistant;

import com.gole.api.chat.application.port.out.SupportAssistantPort;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** AI 분석을 끈 환경의 명시적 no-op. 문의 접수와 관리자 처리는 이 기능에 의존하지 않는다. */
@Component
@ConditionalOnProperty(name = "gole.support-agent.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSupportAssistantAdapter implements SupportAssistantPort {

    @Override
    public Optional<Analysis> analyze(Request request) {
        return Optional.empty();
    }
}
