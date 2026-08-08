package com.gole.api.common.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DiscordOperationalEventPublisherContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JacksonAutoConfiguration.class)
            .withBean(DiscordOperationsProperties.class)
            .withBean(DiscordOperationalEventPublisher.class);

    @Test
    void publisher_usesSpringBootJacksonObjectMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DiscordOperationalEventPublisher.class);
        });
    }
}
