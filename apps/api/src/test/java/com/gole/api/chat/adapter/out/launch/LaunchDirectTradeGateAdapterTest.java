package com.gole.api.chat.adapter.out.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchStage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LaunchDirectTradeGateAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final GetLaunchConfigUseCase launch = mock(GetLaunchConfigUseCase.class);
    private final LaunchDirectTradeGateAdapter adapter = new LaunchDirectTradeGateAdapter(launch);

    @Test
    @DisplayName("채팅 직거래 단계에서는 직거래 완료를 연다")
    void directTradeOpen_inDirectChatStage() {
        when(launch.current()).thenReturn(new LaunchConfig(LaunchStage.PREPARING, Map.of(), NOW, "admin"));

        assertThat(adapter.directTradeOpen()).isTrue();
    }

    @Test
    @DisplayName("결제 거래 단계가 열리면 직거래 완료를 닫는다")
    void directTradeClosed_inPlatformTradingStage() {
        when(launch.current()).thenReturn(new LaunchConfig(LaunchStage.TRADING, Map.of(), NOW, "admin"));

        assertThat(adapter.directTradeOpen()).isFalse();
    }
}
