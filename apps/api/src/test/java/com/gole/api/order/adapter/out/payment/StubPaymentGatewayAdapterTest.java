package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StubPaymentGatewayAdapterTest {

    @Test
    void localStubKeepsDeterministicDeveloperFlow() {
        StubPaymentGatewayAdapter adapter = new StubPaymentGatewayAdapter("local");

        assertThat(adapter.verifyPayment("order-1", 10_000).result())
                .isEqualTo(com.gole.api.order.application.port.out.PaymentGatewayPort.PaymentVerificationResult.PAID);
    }

    @Test
    void productionStubNeverSimulatesPaymentOrRefund() {
        StubPaymentGatewayAdapter adapter = new StubPaymentGatewayAdapter("production");

        assertThatThrownBy(() -> adapter.preparePayment("order-1", 10_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to simulate");
        assertThatThrownBy(() -> adapter.verifyPayment("order-1", 10_000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.refund("order-1", 10_000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.isFullyRefunded("order-1", 10_000)).isInstanceOf(IllegalStateException.class);
    }
}
