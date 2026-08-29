package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ChannelType;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import com.gole.api.order.application.port.out.PaymentGatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class E2EPaymentReadinessIndicatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PortOneReadinessIndicator.class)
            .withBean(E2EPaymentReadinessIndicator.class)
            .withBean(StubPaymentGatewayAdapter.class);

    @Test
    @DisplayName("E2E 프로필과 환경이 함께 맞으면 스텁 카카오페이를 준비 완료로 보고한다")
    void reportsReadyOnlyForExplicitE2EEnvironment() {
        contextRunner
                .withPropertyValues("spring.profiles.active=e2e", "gole.environment=e2e", "portone.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GetPaymentReadinessUseCase.class);
                    assertThat(context).hasSingleBean(PaymentGatewayPort.class);
                    assertThat(context.getBean(GetPaymentReadinessUseCase.class))
                            .isInstanceOf(E2EPaymentReadinessIndicator.class);
                    assertThat(context.getBean(PaymentGatewayPort.class)).isInstanceOf(StubPaymentGatewayAdapter.class);

                    var snapshot =
                            context.getBean(GetPaymentReadinessUseCase.class).getPaymentReadiness();
                    assertThat(snapshot.enabled()).isTrue();
                    assertThat(snapshot.ready()).isTrue();
                    assertThat(snapshot.state()).isEqualTo(State.READY);
                    assertThat(snapshot.channelType()).isEqualTo(ChannelType.TEST);
                    assertThat(snapshot.methods()).containsExactly("KAKAOPAY");
                    assertThat(snapshot.currency()).isEqualTo("KRW");
                    assertThat(snapshot.issues()).isEmpty();
                });
    }

    @Test
    @DisplayName("E2E 프로필만 켜고 환경을 맞추지 않으면 시작 단계에서 실패한다")
    void rejectsE2EProfileOutsideE2EEnvironment() {
        contextRunner
                .withPropertyValues("spring.profiles.active=e2e", "gole.environment=local", "portone.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("GOLE_ENVIRONMENT=e2e");
                });
    }

    @Test
    @DisplayName("E2E 프로필에서 실제 PortOne을 켜면 스텁 보장을 위해 시작을 거부한다")
    void rejectsRealPortOneInE2EProfile() {
        assertThatThrownBy(() -> new E2EPaymentReadinessIndicator("e2e", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_ENABLED=false");
    }

    @Test
    @DisplayName("E2E가 아닌 프로필에서는 기존 PortOne 준비 상태가 유일한 기준이다")
    void normalProfileUsesPortOneReadinessIndicator() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local", "gole.environment=local", "portone.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GetPaymentReadinessUseCase.class);
                    assertThat(context.getBean(GetPaymentReadinessUseCase.class))
                            .isInstanceOf(PortOneReadinessIndicator.class);
                    assertThat(context.getBean(GetPaymentReadinessUseCase.class)
                                    .getPaymentReadiness()
                                    .state())
                            .isEqualTo(State.DISABLED);
                });
    }
}
