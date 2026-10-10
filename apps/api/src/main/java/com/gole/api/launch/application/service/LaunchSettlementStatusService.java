package com.gole.api.launch.application.service;

import com.gole.api.launch.application.port.in.GetLaunchSettlementStatusUseCase;
import com.gole.api.launch.application.port.out.LaunchSettlementModePort;
import org.springframework.stereotype.Service;

/** 정산 실행 모드 조회. 컨트롤러가 아웃바운드 포트를 직접 알지 않게 한다. */
@Service
public class LaunchSettlementStatusService implements GetLaunchSettlementStatusUseCase {

    private final LaunchSettlementModePort settlementMode;

    public LaunchSettlementStatusService(LaunchSettlementModePort settlementMode) {
        this.settlementMode = settlementMode;
    }

    @Override
    public SettlementStatus current() {
        return new SettlementStatus(settlementMode.currentMode(), settlementMode.payoutContractVerified());
    }
}
