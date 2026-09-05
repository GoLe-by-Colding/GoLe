package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gole.api.common.operations.OperationalEventPublisher;
import com.gole.api.order.application.port.in.ConfirmRefundUseCase;
import com.gole.api.order.application.port.in.PayOrderUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class PaymentWebhookControllerContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PayOrderUseCase.class, () -> mock(PayOrderUseCase.class))
            .withBean(ConfirmRefundUseCase.class, () -> mock(ConfirmRefundUseCase.class))
            .withBean(OperationalEventPublisher.class, () -> mock(OperationalEventPublisher.class))
            .withBean(PortOneWebhookVerifier.class, () -> mock(PortOneWebhookVerifier.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(PaymentWebhookController.class);

    @Test
    void paymentDisabledDoesNotExposeWebhookController() {
        contextRunner.withPropertyValues("portone.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(PaymentWebhookController.class);
        });
    }

    @Test
    void paymentEnabledRegistersWebhookController() {
        contextRunner
                .withPropertyValues("portone.enabled=true", "portone.store-id=store-1")
                .run(context -> assertThat(context).hasSingleBean(PaymentWebhookController.class));
    }
}
