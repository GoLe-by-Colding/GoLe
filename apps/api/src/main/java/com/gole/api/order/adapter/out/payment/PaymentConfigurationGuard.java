package com.gole.api.order.adapter.out.payment;

import java.util.Locale;
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
    private final String storeId;
    private final String channelKey;
    private final String cardChannelKey;
    private final String channelType;

    public PaymentConfigurationGuard(
            @Value("${gole.environment:local}") String environment,
            @Value("${portone.enabled:false}") boolean portOneEnabled,
            @Value("${portone.api-secret:}") String apiSecret,
            @Value("${portone.webhook-secret:}") String webhookSecret,
            @Value("${portone.store-id:}") String storeId,
            @Value("${portone.channel-key:}") String channelKey,
            @Value("${portone.card-channel-key:}") String cardChannelKey,
            @Value("${portone.channel-type:TEST}") String channelType) {
        this.environment = environment;
        this.portOneEnabled = portOneEnabled;
        this.apiSecret = apiSecret;
        this.webhookSecret = webhookSecret;
        this.storeId = storeId;
        this.channelKey = channelKey;
        this.cardChannelKey = cardChannelKey;
        this.channelType = channelType;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (portOneEnabled && apiSecret.isBlank()) {
            throw new IllegalStateException("PORTONE_ENABLED=true requires PORTONE_API_SECRET");
        }
        if (portOneEnabled && webhookSecret.isBlank()) {
            throw new IllegalStateException("PORTONE_ENABLED=true requires PORTONE_WEBHOOK_SECRET");
        }
        if (portOneEnabled && storeId.isBlank()) {
            throw new IllegalStateException("PORTONE_ENABLED=true requires PORTONE_STORE_ID");
        }
        if (portOneEnabled && channelKey.isBlank()) {
            throw new IllegalStateException("PORTONE_ENABLED=true requires PORTONE_CHANNEL_KEY");
        }
        if (portOneEnabled && !isValidChannelType(channelType)) {
            throw new IllegalStateException("PORTONE_CHANNEL_TYPE must be TEST or LIVE");
        }
        // 카드 채널은 없어도 되지만, 카카오페이 채널과 같은 값이면 안 된다. 같으면 그 채널 하나가
        // 두 결제수단을 모두 승인하게 되어 "채널이 결제수단을 정한다"는 검증 규칙이 무너진다.
        if (portOneEnabled && !cardChannelKey.isBlank() && cardChannelKey.trim().equals(channelKey.trim())) {
            throw new IllegalStateException("PORTONE_CARD_CHANNEL_KEY must differ from PORTONE_CHANNEL_KEY");
        }
        if (isProduction(environment) && !portOneEnabled) {
            throw new IllegalStateException(
                    "Production must enable PortOne; refusing to start with the stub payment gateway");
        }
    }

    private static boolean isProduction(String environment) {
        return "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }

    private static boolean isValidChannelType(String channelType) {
        if (channelType == null) {
            return false;
        }
        return switch (channelType.trim().toUpperCase(Locale.ROOT)) {
            case "TEST", "LIVE" -> true;
            default -> false;
        };
    }
}
