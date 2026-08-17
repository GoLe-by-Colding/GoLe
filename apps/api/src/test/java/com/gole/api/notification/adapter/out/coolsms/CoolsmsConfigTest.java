package com.gole.api.notification.adapter.out.coolsms;

import static org.assertj.core.api.Assertions.assertThat;

import com.gole.api.notification.application.port.out.AlimtalkSenderPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CoolsmsConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(CoolsmsConfig.class);

    @Test
    void disabledByDefaultDoesNotRegisterAdapter() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(AlimtalkSenderPort.class));
    }

    @Test
    void enabledIntegrationRequiresEveryCredential() {
        contextRunner
                .withPropertyValues(
                        "coolsms.enabled=true",
                        "coolsms.api-key=api-key",
                        "coolsms.api-secret=api-secret",
                        "coolsms.pf-id= ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("COOLSMS_PF_ID");
                });
    }

    @Test
    void enabledAndConfiguredRegistersAdapter() {
        contextRunner
                .withPropertyValues(
                        "coolsms.enabled=true",
                        "coolsms.api-key=api-key",
                        "coolsms.api-secret=api-secret",
                        "coolsms.pf-id=PF-1")
                .run(context -> assertThat(context).hasSingleBean(AlimtalkSenderPort.class));
    }
}
