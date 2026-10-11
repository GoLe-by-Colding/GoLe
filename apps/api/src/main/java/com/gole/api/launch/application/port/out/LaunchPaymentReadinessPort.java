package com.gole.api.launch.application.port.out;

import java.util.List;
import java.util.Optional;

/** Outbound port: 결제 연동 준비 상태. 결제를 여는 단계 전이·안전 클램프가 fail-closed 로 판단할 때 쓴다. 비밀값은 담지 않는다. */
public interface LaunchPaymentReadinessPort {

    /** 상태를 알 수 없으면 비어 있다. 조회 자체가 실패하면 예외를 그대로 던진다. */
    Optional<PaymentReadiness> current();

    /**
     * @param ready 결제를 열어도 되는가(준비 완료 상태)
     * @param state 준비 상태 이름(메시지용)
     * @param problemSettings 문제 있는 설정 이름과 문제("setting(problem)")
     */
    record PaymentReadiness(boolean ready, String state, List<String> problemSettings) {}
}
