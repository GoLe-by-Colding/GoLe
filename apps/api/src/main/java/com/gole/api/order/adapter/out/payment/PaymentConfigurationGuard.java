package com.gole.api.order.adapter.out.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 운영 배포에서 테스트 결제 스텁이 실결제로 오인되는 사고를 시작 단계에서 차단한다. */
@Component
public class PaymentConfigurationGuard implements ApplicationRunner {

    private final String environment;
    private final boolean portOneEnabled;
    private final String apiSecret;
    private final String webhookSecret;

    public PaymentConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${portone.enabled:false}") boolean portOneEnabled,
            @Value("${portone.api-secret:}") String apiSecret,
            @Value("${portone.webhook-secret:}") String webhookSecret) {
        this.environment = environment;
        this.portOneEnabled = portOneEnabled;
        this.apiSecret = apiSecret;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (portOneEnabled && apiSecret.isBlank()) {
            throw new IllegalStateException("PORTONE_ENABLED=true requires PORTONE_API_SECRET");
        }
        if (portOneEnabled && webhookSecret.isBlank()) {
            throw new IllegalStateException("PORTONE_ENABLED=true requires PORTONE_WEBHOOK_SECRET");
        }
        if (isProduction(environment) && !portOneEnabled) {
            throw new IllegalStateException(
                    "Production must enable PortOne; refusing to start with the stub payment gateway");
        }
    }

    private static boolean isProduction(String environment) {
        return "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }
}
