package com.gole.api.notification.adapter.out.coolsms;

import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CoolSMS 알림톡 출력 어댑터 조립. */
@Configuration
@EnableConfigurationProperties(CoolsmsProperties.class)
public class CoolsmsConfig {

    @Bean
    @ConditionalOnProperty(name = "coolsms.enabled", havingValue = "true")
    public AlimtalkSenderPort alimtalkSenderPort(CoolsmsProperties properties) {
        properties.validateEnabledConfiguration();
        return new CoolsmsAlimtalkAdapter(properties);
    }
}
