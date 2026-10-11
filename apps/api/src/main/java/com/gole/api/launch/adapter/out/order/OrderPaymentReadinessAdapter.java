package com.gole.api.launch.adapter.out.order;

import com.gole.api.launch.application.port.out.LaunchPaymentReadinessPort;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Snapshot;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 주문 컨텍스트 통합 어댑터. 결제 준비 스냅샷을 공개 단계 판단에 필요한 값으로 환원한다. */
@Component
public class OrderPaymentReadinessAdapter implements LaunchPaymentReadinessPort {

    private final GetPaymentReadinessUseCase paymentReadiness;

    public OrderPaymentReadinessAdapter(GetPaymentReadinessUseCase paymentReadiness) {
        this.paymentReadiness = paymentReadiness;
    }

    @Override
    public Optional<PaymentReadiness> current() {
        Snapshot snapshot = paymentReadiness.getPaymentReadiness();
        if (snapshot == null) {
            return Optional.empty();
        }
        List<String> problems = snapshot.issues() == null
                ? List.of()
                : snapshot.issues().stream()
                        .map(issue -> issue.setting() + "(" + issue.problem() + ")")
                        .toList();
        return Optional.of(new PaymentReadiness(
                snapshot.ready() && snapshot.state() == State.READY, String.valueOf(snapshot.state()), problems));
    }
}
