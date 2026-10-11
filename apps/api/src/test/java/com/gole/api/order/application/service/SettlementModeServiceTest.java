package com.gole.api.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.order.application.port.in.GetSettlementModeUseCase.Mode;
import com.gole.api.order.application.port.in.GetSettlementModeUseCase.SettlementMode;
import com.gole.api.order.config.SettlementProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementModeServiceTest {

    @Test
    @DisplayName("설정의 정산 모드와 지급 계약 확인값을 그대로 낸다")
    void current_reflectsProperties() {
        SettlementProperties properties = new SettlementProperties();
        properties.setMode(SettlementProperties.Mode.MANUAL);
        properties.setPayoutContractVerified(true);

        assertThat(new SettlementModeService(properties).current()).isEqualTo(new SettlementMode(Mode.MANUAL, true));
    }

    @Test
    @DisplayName("모드가 비어 있으면 안전 기본값 DISABLED 다")
    void current_defaultsToDisabled() {
        SettlementProperties properties = new SettlementProperties();
        properties.setMode(null);

        assertThat(new SettlementModeService(properties).current().mode()).isEqualTo(Mode.DISABLED);
    }
}
