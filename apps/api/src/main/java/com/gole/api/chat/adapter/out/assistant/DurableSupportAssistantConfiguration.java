package com.gole.api.chat.adapter.out.assistant;

import com.gole.api.chat.application.port.out.SupportAssistantPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gole.support-agent.durable.enabled", havingValue = "true")
public class DurableSupportAssistantConfiguration {
    @Bean
    @ConditionalOnProperty(name = "gole.support-agent.enabled", havingValue = "true")
    SupportAssistantPort durableSupportAssistantPort(DurableSupportAssistantAdapter adapter) {
        return new SupportAssistantPort() {
            @Override
            public java.util.Optional<Analysis> analyze(Request request) {
                return adapter.analyze(request);
            }

            @Override
            public boolean usesDurableJobs() {
                return true;
            }
        };
    }
}
