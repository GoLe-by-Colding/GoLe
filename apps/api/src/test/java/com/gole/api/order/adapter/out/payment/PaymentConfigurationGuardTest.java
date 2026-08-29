package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class PaymentConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void productionMayBootWithPaymentsDisabledForDirectTradeLaunch() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("production", false, "", "", "", "", "", "TEST");

        assertThatCode(() -> guard.run(NO_ARGS)).doesNotThrowAnyException();
    }

    @Test
    void enabledPortOneRequiresSecret() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("local", true, " ", "webhook-secret", "store-1", "channel-1", "", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_API_SECRET");
    }

    @Test
    void enabledPortOneRequiresWebhookSecret() {
        PaymentConfigurationGuard guard =
                new PaymentConfigurationGuard("local", true, "api-secret", " ", "store-1", "channel-1", "", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_WEBHOOK_SECRET");
    }

    @Test
    void enabledPortOneRequiresStoreId() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard(
                "local", true, "api-secret", "webhook-secret", " ", "channel-1", "", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_STORE_ID");
    }

    @Test
    void enabledPortOneRequiresChannelKey() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard(
                "local", true, "api-secret", "webhook-secret", "store-1", " ", "", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_CHANNEL_KEY");
    }

    @Test
    void enabledPortOneRejectsUnknownChannelType() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard(
                "local", true, "api-secret", "webhook-secret", "store-1", "channel-1", "", "SANDBOX");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_CHANNEL_TYPE");
    }

    /**
     * 두 채널 키가 같으면 그 채널 하나가 간편결제와 카드를 모두 승인하게 된다. 어댑터의
     * "채널이 결제수단을 정한다"는 규칙이 설정 단계에서 무너지므로 기동을 막는다.
     */
    @Test
    @DisplayName("카드 채널 키가 카카오페이 채널 키와 같으면 기동을 거부한다")
    void rejectsCardChannelKeyIdenticalToKakaoPayChannelKey() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard(
                "local", true, "api-secret", "webhook-secret", "store-1", "channel-1", " channel-1 ", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_CARD_CHANNEL_KEY");
    }

    @Test
    @DisplayName("카드 채널은 선택 설정이다 — 비어 있어도, 서로 다른 값이어도 기동한다")
    void allowsAbsentOrDistinctCardChannel() {
        assertThatCode(() -> new PaymentConfigurationGuard(
                                "local", true, "api-secret", "webhook-secret", "store-1", "channel-1", "", "TEST")
                        .run(NO_ARGS))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PaymentConfigurationGuard(
                                "local", true, "api-secret", "webhook-secret", "store-1", "channel-1", "card-1", "TEST")
                        .run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void localStubAndConfiguredProductionAreAllowed() {
        assertThatCode(() -> new PaymentConfigurationGuard("local", false, "", "", "", "", "", "TEST").run(NO_ARGS))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PaymentConfigurationGuard(
                                "prod", true, "api-secret", "webhook-secret", "store-1", "channel-1", "", "live")
                        .run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsTestPaymentChannel() {
        PaymentConfigurationGuard guard = new PaymentConfigurationGuard(
                "production", true, "api-secret", "webhook-secret", "store-1", "channel-1", "", "TEST");

        assertThatThrownBy(() -> guard.run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORTONE_CHANNEL_TYPE=LIVE");
    }
}
