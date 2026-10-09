package com.gole.api.launch.adapter.out.config;

import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import com.gole.api.launch.domain.model.SettlementMode;
import com.gole.api.order.application.port.in.GetSettlementModeUseCase;
import org.springframework.stereotype.Component;

/** 실제 정산 실행 모드를 공개 단계 검증에 제공한다. order 의 인바운드 포트에서 읽는다. */
@Component
public class SettlementModeLaunchAdapter implements LaunchSettlementModePort {

    private final GetSettlementModeUseCase settlementMode;

    public SettlementModeLaunchAdapter(GetSettlementModeUseCase settlementMode) {
        this.settlementMode = settlementMode;
    }

    @Override
    public SettlementMode currentMode() {
        return switch (settlementMode.current().mode()) {
            case DISABLED -> SettlementMode.DISABLED;
            case MANUAL -> SettlementMode.MANUAL;
            case PROVIDER -> SettlementMode.PROVIDER;
        };
    }

    @Override
    public boolean payoutContractVerified() {
        return settlementMode.current().payoutContractVerified();
    }
}
