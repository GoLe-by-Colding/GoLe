package com.gole.api.chat.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class SupportAssistantConfigurationGuardTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    @Test
    void disabledAgentNeedsNoGrpcTarget() {
        assertThatCode(() -> new SupportAssistantConfigurationGuard("local", false, "", Duration.ZERO).run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledAgentRequiresBoundedGrpcConfiguration() {
        assertThatThrownBy(() ->
                        new SupportAssistantConfigurationGuard("local", true, "", Duration.ofSeconds(2)).run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRPC_TARGET");
        assertThatThrownBy(() -> new SupportAssistantConfigurationGuard(
                                "local", true, "support-agent:50051", Duration.ofSeconds(11))
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between");
        assertThatCode(() -> new SupportAssistantConfigurationGuard(
                                "production", true, "support-agent:50051", Duration.ofSeconds(2))
                        .run(NO_ARGS))
                .doesNotThrowAnyException();
    }

    @Test
    void publicEnvironmentRejectsExternalPlaintextTargetOrDifferentTimeout() {
        assertThatThrownBy(() -> new SupportAssistantConfigurationGuard(
                                "production", true, "external.example:50051", Duration.ofSeconds(2))
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal");
        assertThatThrownBy(() -> new SupportAssistantConfigurationGuard(
                                "production", true, "support-agent:50051", Duration.ofSeconds(3))
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PT2S");
    }

    @Test
    void unknownEnvironmentCannotSendInquiryTextToExternalPlaintextTarget() {
        assertThatThrownBy(() -> new SupportAssistantConfigurationGuard(
                                "production-typo", true, "external.example:50051", Duration.ofSeconds(2))
                        .run(NO_ARGS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal");
    }
}
