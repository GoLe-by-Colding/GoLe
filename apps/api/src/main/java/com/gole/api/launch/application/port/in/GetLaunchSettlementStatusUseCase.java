package com.gole.api.launch.application.port.in;

import com.gole.api.launch.domain.model.SettlementMode;

/** Inbound port: 공개 단계 화면에 보일 실제 정산 실행 모드와 지급 계약 확인값. */
public interface GetLaunchSettlementStatusUseCase {

    SettlementStatus current();

    record SettlementStatus(SettlementMode mode, boolean payoutContractVerified) {}
}
