package com.gole.api.chat.adapter.out.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gole.api.chat.application.port.out.SupportAssistantPort;
import com.gole.api.chat.application.port.out.SupportAssistantPurgePort;
import com.gole.api.chat.application.port.out.SupportAssistantWorkSourcePort;
import com.gole.api.chat.application.port.out.SupportTicketRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DurableSupportAssistantConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(value -> value.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withUserConfiguration(
                    DisabledSupportAssistantAdapter.class,
                    GrpcSupportAssistantAdapter.class,
                    DisabledSupportAssistantPurgeAdapter.class,
                    DurableSupportAssistantAdapter.class,
                    DurableSupportAssistantConfiguration.class)
            .withBean(SupportTicketRepositoryPort.class, () -> mock(SupportTicketRepositoryPort.class))
            .withBean(SupportAssistantWorkSourcePort.class, () -> mock(SupportAssistantWorkSourcePort.class))
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build());

    @Test
    @DisplayName("문서화한 환경변수 이름으로 durable opt-in을 선택함")
    void environmentVariablesSelectDurableMode() {
        context.withInitializer(value -> value.getEnvironment()
                        .getPropertySources()
                        .addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                                "test-env",
                                java.util.Map.of(
                                        "GOLE_SUPPORT_AGENT_ENABLED", "true",
                                        "GOLE_SUPPORT_AGENT_DURABLE_ENABLED", "true",
                                        "GOLE_SUPPORT_AGENT_DURABLE_CALLER", "java",
                                        "GOLE_SUPPORT_AGENT_DURABLE_TOKEN",
                                                "test-internal-credential-longer-than-32"))))
                .run(value -> {
                    assertThat(value).hasSingleBean(SupportAssistantPort.class);
                    assertThat(value).doesNotHaveBean(GrpcSupportAssistantAdapter.class);
                    assertThat(value.getBean(SupportAssistantPurgePort.class))
                            .isInstanceOf(DurableSupportAssistantAdapter.class);
                });
    }

    @Test
    @DisplayName("기본값은 기존 disabled 어댑터이며 원격 채널을 만들지 않음")
    void defaultsRemainDisabled() {
        context.run(value -> {
            assertThat(value).hasSingleBean(SupportAssistantPort.class);
            assertThat(value.getBean(SupportAssistantPort.class)).isInstanceOf(DisabledSupportAssistantAdapter.class);
            assertThat(value.getBean(SupportAssistantPurgePort.class))
                    .isInstanceOf(DisabledSupportAssistantPurgeAdapter.class);
        });
    }

    @Test
    @DisplayName("기존 enabled 설정만으로는 동기 Analyze 어댑터를 유지함")
    void existingEnabledModeRemainsSynchronous() {
        context.withPropertyValues("gole.support-agent.enabled=true", "gole.support-agent.target=127.0.0.1:50051")
                .run(value -> {
                    assertThat(value).hasSingleBean(SupportAssistantPort.class);
                    assertThat(value.getBean(SupportAssistantPort.class))
                            .isInstanceOf(GrpcSupportAssistantAdapter.class);
                });
    }

    @Test
    @DisplayName("durable opt-in은 분석 포트를 교체하되 분석을 꺼도 파기 포트를 유지함")
    void durablePurgeRemainsAvailableWhenAnalysisIsDisabled() {
        var configured = context.withPropertyValues(
                "gole.support-agent.durable.enabled=true",
                "gole.support-agent.durable.caller=java",
                "gole.support-agent.durable.token=test-internal-credential-longer-than-32");
        configured.withPropertyValues("gole.support-agent.enabled=true").run(value -> {
            assertThat(value).hasSingleBean(SupportAssistantPort.class);
            assertThat(value).doesNotHaveBean(GrpcSupportAssistantAdapter.class);
            assertThat(value.getBean(SupportAssistantPurgePort.class))
                    .isInstanceOf(DurableSupportAssistantAdapter.class);
        });
        configured.withPropertyValues("gole.support-agent.enabled=false").run(value -> {
            assertThat(value).hasSingleBean(SupportAssistantPort.class);
            assertThat(value.getBean(SupportAssistantPort.class)).isInstanceOf(DisabledSupportAssistantAdapter.class);
            assertThat(value.getBean(SupportAssistantPurgePort.class))
                    .isInstanceOf(DurableSupportAssistantAdapter.class);
        });
    }
}
