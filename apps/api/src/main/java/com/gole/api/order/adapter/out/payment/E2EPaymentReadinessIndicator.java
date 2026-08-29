package com.gole.api.order.adapter.out.payment;

import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ChannelType;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 실제 PG를 호출하지 않는 E2E 시나리오에서만 결제 기능 게이트를 여는 준비 상태다. */
@Component
@Profile("e2e")
public final class E2EPaymentReadinessIndicator implements GetPaymentReadinessUseCase {

    public E2EPaymentReadinessIndicator(
            @Value("${gole.environment:local}") String environment,
            @Value("${portone.enabled:false}") boolean portOneEnabled) {
        if (!"e2e".equalsIgnoreCase(environment == null ? "" : environment.trim())) {
            throw new IllegalStateException("The e2e Spring profile requires GOLE_ENVIRONMENT=e2e");
        }
        if (portOneEnabled) {
            throw new IllegalStateException("The e2e Spring profile requires PORTONE_ENABLED=false and the stub PG");
        }
    }

    @Override
    public Snapshot getPaymentReadiness() {
        return new Snapshot(true, true, State.READY, ChannelType.TEST, List.of("KAKAOPAY"), "KRW", List.of());
    }
}
