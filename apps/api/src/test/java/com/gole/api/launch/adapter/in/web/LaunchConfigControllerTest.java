package com.gole.api.launch.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gole.api.account.config.EmailAuthenticationAvailability;
import com.gole.api.common.config.SellerIdentityVerificationProperties;
import com.gole.api.launch.adapter.in.web.LaunchDtos.LaunchConfigResponse;
import com.gole.api.launch.application.port.in.GetLaunchConfigUseCase;
import com.gole.api.launch.domain.model.LaunchConfig;
import com.gole.api.launch.domain.model.LaunchFeature;
import com.gole.api.launch.domain.model.LaunchStage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 공개 응답은 프론트와 고정된 계약이다. 필드 이름·타입이 바뀌면 프론트가 Stage 0 으로 닫히므로 직렬화 결과를 직접 단정한다. */
class LaunchConfigControllerTest {

    private final GetLaunchConfigUseCase launchConfig = mock(GetLaunchConfigUseCase.class);
    private final SellerIdentityVerificationProperties sellerIdentityVerification =
            new SellerIdentityVerificationProperties();
    private final LaunchConfigController controller = new LaunchConfigController(
            launchConfig, sellerIdentityVerification, new EmailAuthenticationAvailability("test", false));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("공개 응답은 stage·tradeMode·features·updatedAt 계약을 지킨다")
    void publicResponseKeepsTheAgreedContract() {
        Instant updatedAt = Instant.parse("2026-08-29T01:02:03Z");
        when(launchConfig.current()).thenReturn(new LaunchConfig(LaunchStage.TRADING, Map.of(), updatedAt, "admin-1"));

        LaunchConfigResponse response = controller.launch();
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.stage()).isEqualTo(2);
        assertThat(response.tradeMode()).isEqualTo("MANUAL_SETTLEMENT");
        assertThat(response.features().payments()).isTrue();
        assertThat(response.features().reviews()).isTrue();
        assertThat(response.features().partnerPayout()).isFalse();
        assertThat(response.sellerIdentityVerificationReady()).isFalse();
        assertThat(response.emailAuthenticationAvailable()).isTrue();
        assertThat(json)
                .contains("\"stage\":2")
                .contains("\"tradeMode\":\"MANUAL_SETTLEMENT\"")
                .contains("\"payments\":true")
                .contains("\"reviews\":true")
                .contains("\"partnerPayout\":false")
                .contains("\"sellerIdentityVerificationReady\":false")
                .contains("\"emailAuthenticationAvailable\":true");
    }

    @Test
    @DisplayName("한 번도 저장되지 않았으면 updatedAt 은 null 이다")
    void updatedAtIsNullWhenNeverSaved() {
        when(launchConfig.current()).thenReturn(LaunchConfig.unset());

        LaunchConfigResponse response = controller.launch();

        assertThat(response.updatedAt()).isNull();
        assertThat(objectMapper.writeValueAsString(response)).contains("\"updatedAt\":null");
    }

    @Test
    @DisplayName("직거래 단계는 tradeMode=DIRECT_CHAT 과 전 기능 닫힘으로 나간다")
    void directChatStageReportsClosedFeatures() {
        when(launchConfig.current()).thenReturn(new LaunchConfig(LaunchStage.PREPARING, Map.of(), null, null));

        LaunchConfigResponse response = controller.launch();

        assertThat(response.stage()).isZero();
        assertThat(response.tradeMode()).isEqualTo("DIRECT_CHAT");
        assertThat(response.features().payments()).isFalse();
    }

    @Test
    @DisplayName("override 는 공개 응답의 features 에 그대로 반영된다")
    void overridesAreReflectedInPublicFeatures() {
        when(launchConfig.current())
                .thenReturn(new LaunchConfig(LaunchStage.FULL, Map.of(LaunchFeature.PAYMENTS, false), null, null));

        assertThat(controller.launch().features().payments()).isFalse();
    }

    @Test
    @DisplayName("판매자 신원확인 준비 상태는 배포 래치의 실제 값을 공개한다")
    void sellerIdentityReadinessReflectsDeploymentLatch() {
        sellerIdentityVerification.setVerificationReady(true);
        when(launchConfig.current()).thenReturn(LaunchConfig.unset());

        assertThat(controller.launch().sellerIdentityVerificationReady()).isTrue();
    }
}
