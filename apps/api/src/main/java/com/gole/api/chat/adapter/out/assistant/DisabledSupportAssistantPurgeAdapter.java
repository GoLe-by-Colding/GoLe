package com.gole.api.chat.adapter.out.assistant;

import com.gole.api.chat.application.port.out.SupportAssistantPurgePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 영속 원격 분석을 사용하지 않는 기존 구성에는 파기할 원격 사본이 없다. */
@Component
@ConditionalOnProperty(name = "gole.support-agent.durable.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSupportAssistantPurgeAdapter implements SupportAssistantPurgePort {
    @Override
    public void requireAvailable(boolean remoteCopyPossible) {
        if (remoteCopyPossible) {
            throw new IllegalStateException("SUPPORT_ASSISTANT_PURGE_CONFIGURATION_REQUIRED");
        }
    }

    @Override
    public void purge(String roomId, String requesterId) {}
}
