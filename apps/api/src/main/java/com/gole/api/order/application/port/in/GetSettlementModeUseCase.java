package com.gole.api.order.application.port.in;

/** Inbound port: 실제 정산 실행 모드와 지급 계약 확인값. 출시 단계 검증이 order 의 설정 클래스 대신 이것을 본다. */
public interface GetSettlementModeUseCase {

    SettlementMode current();

    /** @param payoutContractVerified 서면 PG/지급대행 계약을 운영자가 확인했는가. true 만으로 계약을 대신하지 않는다. */
    record SettlementMode(Mode mode, boolean payoutContractVerified) {}

    enum Mode {
        DISABLED,
        MANUAL,
        PROVIDER
    }
}
