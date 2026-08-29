package com.gole.api.launch.application.port.out;

/**
 * 공개 단계가 의존하는 실제 정산 실행 모드.
 *
 * <p>launch 애플리케이션 계층이 order의 Spring 설정 클래스에 직접 의존하지 않도록 작은 포트로
 * 분리한다. 관리자 화면에 보이는 단계와 서버가 실제로 돈을 보내는 방식이 다르면 새 거래를
 * 열어서는 안 된다.
 */
public interface LaunchSettlementModePort {

    enum Mode {
        DISABLED,
        MANUAL,
        PROVIDER
    }

    Mode currentMode();

    /** 서면 PG/지급대행 계약을 운영자가 확인했는가. true만으로 계약을 대신하지 않는다. */
    boolean payoutContractVerified();
}
