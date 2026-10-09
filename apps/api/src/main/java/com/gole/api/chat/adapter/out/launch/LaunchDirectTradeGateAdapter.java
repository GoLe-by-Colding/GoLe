package com.gole.api.chat.adapter.out.launch;

import com.gole.api.chat.application.port.out.DirectTradeGatePort;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.TradeMode;
import org.springframework.stereotype.Component;

/** 출시 단계 통합 어댑터. 거래 방식이 채팅 직거래일 때만 직거래 완료를 연다. */
@Component
public class LaunchDirectTradeGateAdapter implements DirectTradeGatePort {

    private final GetLaunchConfigUseCase launchConfig;

    public LaunchDirectTradeGateAdapter(GetLaunchConfigUseCase launchConfig) {
        this.launchConfig = launchConfig;
    }

    @Override
    public boolean directTradeOpen() {
        return launchConfig.current().tradeMode() == TradeMode.DIRECT_CHAT;
    }
}
