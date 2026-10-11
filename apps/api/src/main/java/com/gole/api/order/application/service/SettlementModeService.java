package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.GetSettlementModeUseCase;
import com.gole.api.order.config.SettlementProperties;
import org.springframework.stereotype.Service;

/** {@code gole.settlement.*} 설정에서 정산 실행 모드를 읽는다. 값이 없으면 안전 기본값(DISABLED)이다. */
@Service
public class SettlementModeService implements GetSettlementModeUseCase {

    private final SettlementProperties properties;

    public SettlementModeService(SettlementProperties properties) {
        this.properties = properties;
    }

    @Override
    public SettlementMode current() {
        SettlementProperties.Mode mode = properties.getMode();
        Mode current = mode == null
                ? Mode.DISABLED
                : switch (mode) {
                    case DISABLED -> Mode.DISABLED;
                    case MANUAL -> Mode.MANUAL;
                    case PROVIDER -> Mode.PROVIDER;
                };
        return new SettlementMode(current, properties.isPayoutContractVerified());
    }
}
