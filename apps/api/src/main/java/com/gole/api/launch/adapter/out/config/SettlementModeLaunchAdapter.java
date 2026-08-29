package com.gole.api.launch.adapter.out.config;

import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.order.adapter.out.settlement.SettlementProperties;
import org.springframework.stereotype.Component;

/** 실제 {@code gole.settlement.mode}를 공개 단계 검증에 제공한다. */
@Component
public class SettlementModeLaunchAdapter implements LaunchSettlementModePort {

    private final SettlementProperties properties;

    public SettlementModeLaunchAdapter(SettlementProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mode currentMode() {
        SettlementProperties.Mode mode = properties.getMode();
        if (mode == null) {
            return Mode.DISABLED;
        }
        return switch (mode) {
            case DISABLED -> Mode.DISABLED;
            case MANUAL -> Mode.MANUAL;
            case PROVIDER -> Mode.PROVIDER;
        };
    }

    @Override
    public boolean payoutContractVerified() {
        return properties.isPayoutContractVerified();
    }
}
