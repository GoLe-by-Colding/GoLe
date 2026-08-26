package com.gole.api.order.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.ChannelType;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.Problem;
import com.gole.api.order.application.port.in.GetPaymentReadinessUseCase.State;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PortOneReadinessIndicatorTest {

    @Test
    @DisplayName("활성화된 카카오페이 TEST 설정은 준비 완료로 표시한다")
    void reportsReadyTestConfigurationWithoutSecretValues() throws Exception {
        PortOneReadinessIndicator indicator = new PortOneReadinessIndicator(
                true,
                "do-not-expose-api-secret",
                "do-not-expose-webhook-secret",
                "do-not-expose-store-id",
                "do-not-expose-channel-key",
                "",
                "TEST");

        var snapshot = indicator.getPaymentReadiness();
        String json = new ObjectMapper().writeValueAsString(snapshot);

        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.state()).isEqualTo(State.READY);
        assertThat(snapshot.channelType()).isEqualTo(ChannelType.TEST);
        assertThat(snapshot.methods()).containsExactly("KAKAOPAY");
        assertThat(snapshot.currency()).isEqualTo("KRW");
        assertThat(snapshot.issues()).isEmpty();
        assertThat(json)
                .doesNotContain(
                        "do-not-expose-api-secret",
                        "do-not-expose-webhook-secret",
                        "do-not-expose-store-id",
                        "do-not-expose-channel-key");
    }

    /** 카드 채널 키가 있으면 카드가 열린 것이다. 운영자가 대시보드에서 그 사실을 봐야 한다. */
    @Test
    @DisplayName("카드 채널이 설정되면 열린 결제수단에 카드를 함께 보고한다")
    void reportsCardMethodWhenCardChannelIsConfigured() throws Exception {
        PortOneReadinessIndicator indicator = new PortOneReadinessIndicator(
                true, "api-secret", "webhook-secret", "store-id", "channel-key", "do-not-expose-card-channel", "TEST");

        var snapshot = indicator.getPaymentReadiness();

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.methods()).containsExactly("KAKAOPAY", "CARD");
        assertThat(new ObjectMapper().writeValueAsString(snapshot)).doesNotContain("do-not-expose-card-channel");
    }

    @Test
    @DisplayName("활성화됐지만 설정이 비었거나 잘못되면 안전한 설정 이름만 진단한다")
    void reportsMissingAndInvalidConfiguration() {
        PortOneReadinessIndicator indicator = new PortOneReadinessIndicator(
                true, "api-secret", " ", "", "channel-key", "", "sandbox-secret-like-value");

        var snapshot = indicator.getPaymentReadiness();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.state()).isEqualTo(State.MISCONFIGURED);
        assertThat(snapshot.channelType()).isEqualTo(ChannelType.UNKNOWN);
        assertThat(snapshot.issues())
                .extracting(issue -> issue.setting() + ":" + issue.problem())
                .containsExactly(
                        "PORTONE_WEBHOOK_SECRET:" + Problem.MISSING,
                        "PORTONE_STORE_ID:" + Problem.MISSING,
                        "PORTONE_CHANNEL_TYPE:" + Problem.INVALID);
        assertThat(snapshot.toString()).doesNotContain("sandbox-secret-like-value", "api-secret", "channel-key");
    }

    @Test
    @DisplayName("비활성 상태에서도 활성화 전에 채워야 할 설정 목록을 확인할 수 있다")
    void reportsDisabledStateAndMissingConfiguration() {
        var snapshot = new PortOneReadinessIndicator(false, "", "", "", "", "", "TEST").getPaymentReadiness();

        assertThat(snapshot.enabled()).isFalse();
        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.state()).isEqualTo(State.DISABLED);
        assertThat(snapshot.channelType()).isEqualTo(ChannelType.TEST);
        assertThat(snapshot.issues())
                .extracting(issue -> issue.setting())
                .containsExactlyElementsOf(List.of(
                        "PORTONE_API_SECRET", "PORTONE_WEBHOOK_SECRET", "PORTONE_STORE_ID", "PORTONE_CHANNEL_KEY"));
    }

    @Test
    @DisplayName("LIVE 채널은 별도 모드로 명확히 식별한다")
    void reportsLiveChannel() {
        var snapshot = new PortOneReadinessIndicator(
                        true, "api-secret", "webhook-secret", "store-id", "channel-key", "", "live")
                .getPaymentReadiness();

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.channelType()).isEqualTo(ChannelType.LIVE);
    }
}
