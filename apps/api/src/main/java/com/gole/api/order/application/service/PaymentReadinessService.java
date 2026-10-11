package com.gole.api.order.application.service;

import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.out.PaymentReadinessProbePort;
import org.springframework.stereotype.Service;

/** 관리자·출시 관리 화면의 결제 연동 준비 상태 조회. 점검은 프로필별 어댑터가 한다. */
@Service
public class PaymentReadinessService implements GetPaymentReadinessUseCase {

    private final PaymentReadinessProbePort probe;

    public PaymentReadinessService(PaymentReadinessProbePort probe) {
        this.probe = probe;
    }

    @Override
    public Snapshot getPaymentReadiness() {
        return probe.getPaymentReadiness();
    }
}
