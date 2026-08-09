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
    private final String channelType;

    public PortOneReadinessIndicator(
            @Value("${portone.enabled:false}") boolean enabled,
            @Value("${portone.api-secret:}") String apiSecret,
            @Value("${portone.webhook-secret:}") String webhookSecret,
            @Value("${portone.store-id:}") String storeId,
            @Value("${portone.channel-key:}") String channelKey,
            @Value("${portone.channel-type:TEST}") String channelType) {
        this.enabled = enabled;
        this.apiSecret = apiSecret;
        this.webhookSecret = webhookSecret;
        this.storeId = storeId;
        this.channelKey = channelKey;
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
                enabled, state == State.READY, state, safeChannelType, "KAKAOPAY", "KRW", List.copyOf(issues));
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
