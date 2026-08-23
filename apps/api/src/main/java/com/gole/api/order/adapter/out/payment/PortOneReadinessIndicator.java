package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ChannelType;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ConfigurationIssue;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Problem;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 관리자 운영 화면에 제공할 PortOne 준비 상태를 계산한다.
 *
 * <p>설정값의 존재 여부만 다루며 API secret, webhook secret, store ID, channel key 원문은
 * snapshot에 포함하지 않는다.
 */
@Component
public class PortOneReadinessIndicator implements GetPaymentReadinessUseCase {

    private final boolean enabled;
    private final String apiSecret;
    private final String webhookSecret;
    private final String storeId;
    private final String channelKey;
    private final String cardChannelKey;
    private final String channelType;

    public PortOneReadinessIndicator(
            @Value("${portone.enabled:false}") boolean enabled,
            @Value("${portone.api-secret:}") String apiSecret,
            @Value("${portone.webhook-secret:}") String webhookSecret,
            @Value("${portone.store-id:}") String storeId,
            @Value("${portone.channel-key:}") String channelKey,
            @Value("${portone.card-channel-key:}") String cardChannelKey,
            @Value("${portone.channel-type:TEST}") String channelType) {
        this.enabled = enabled;
        this.apiSecret = apiSecret;
        this.webhookSecret = webhookSecret;
        this.storeId = storeId;
        this.channelKey = channelKey;
        this.cardChannelKey = cardChannelKey;
        this.channelType = channelType;
    }

    @Override
    public Snapshot getPaymentReadiness() {
        List<ConfigurationIssue> issues = new ArrayList<>();
        addMissing(issues, "PORTONE_API_SECRET", apiSecret);
        addMissing(issues, "PORTONE_WEBHOOK_SECRET", webhookSecret);
        addMissing(issues, "PORTONE_STORE_ID", storeId);
        addMissing(issues, "PORTONE_CHANNEL_KEY", channelKey);

        ChannelType safeChannelType = parseChannelType(channelType);
        if (safeChannelType == ChannelType.UNKNOWN) {
            issues.add(new ConfigurationIssue(
                    "PORTONE_CHANNEL_TYPE",
                    channelType == null || channelType.isBlank() ? Problem.MISSING : Problem.INVALID));
        }

        State state = !enabled ? State.DISABLED : issues.isEmpty() ? State.READY : State.MISCONFIGURED;
        return new Snapshot(
                enabled, state == State.READY, state, safeChannelType, openMethods(), "KRW", List.copyOf(issues));
    }

    /**
     * 지금 열려 있는 결제수단. 카카오페이는 필수 설정이라 항상 있고, 카드는 채널 키가 있을 때만
     * 열린다. 여기 없는 수단으로 낸 결제는 어댑터가 승인하지 않는다.
     */
    private List<String> openMethods() {
        return cardChannelKey == null || cardChannelKey.isBlank() ? List.of("KAKAOPAY") : List.of("KAKAOPAY", "CARD");
    }

    private static void addMissing(List<ConfigurationIssue> issues, String setting, String value) {
        if (value == null || value.isBlank()) {
            issues.add(new ConfigurationIssue(setting, Problem.MISSING));
        }
    }

    private static ChannelType parseChannelType(String value) {
        if (value == null) {
            return ChannelType.UNKNOWN;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "TEST" -> ChannelType.TEST;
            case "LIVE" -> ChannelType.LIVE;
            default -> ChannelType.UNKNOWN;
        };
    }
}
