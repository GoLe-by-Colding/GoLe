package com.gole.api.order.application.port.out;

import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;

/** Outbound port: 결제 연동 설정 점검. 실행 프로필에 맞는 구현(운영 PortOne·E2E 고정값)이 하나만 뜬다. 비밀값은 내주지 않는다. */
public interface PaymentReadinessProbePort {

    Snapshot getPaymentReadiness();
}
