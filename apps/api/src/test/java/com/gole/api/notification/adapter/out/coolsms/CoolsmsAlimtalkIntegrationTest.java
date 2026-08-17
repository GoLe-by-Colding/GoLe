package com.gole.api.notification.adapter.out.coolsms;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.AlimtalkAcceptance;
import com.gole.api.notification.application.port.out.AlimtalkSenderPort.SendAlimtalkCommand;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class CoolsmsAlimtalkIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @EnabledIfEnvironmentVariable(named = "COOLSMS_SMOKE_TEST_ENABLED", matches = "true")
    void sendsOneAlimtalkUsingLocalEnvironment() throws Exception {
        CoolsmsProperties properties = new CoolsmsProperties(
                true,
                requiredEnvironmentVariable("COOLSMS_API_KEY"),
                requiredEnvironmentVariable("COOLSMS_API_SECRET"),
                requiredEnvironmentVariable("COOLSMS_PF_ID"));
        properties.validateEnabledConfiguration();
        CoolsmsAlimtalkAdapter adapter = new CoolsmsAlimtalkAdapter(properties);
        Map<String, String> variables = OBJECT_MAPPER.readValue(
                requiredEnvironmentVariable("COOLSMS_TEST_VARIABLES_JSON"), new TypeReference<>() {});

        AlimtalkAcceptance acceptance = adapter.send(new SendAlimtalkCommand(
                requiredEnvironmentVariable("COOLSMS_TEST_TO"),
                requiredEnvironmentVariable("COOLSMS_TEST_TEMPLATE_ID"),
                variables));

        assertThat(acceptance.groupId()).isNotBlank();
        assertThat(acceptance.messageId()).isNotBlank();
        assertThat(acceptance.statusCode()).isEqualTo("2000");
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for the CoolSMS smoke test");
        }
        return value;
    }
}
