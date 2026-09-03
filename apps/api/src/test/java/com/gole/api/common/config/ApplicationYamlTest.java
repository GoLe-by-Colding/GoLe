package com.gole.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ApplicationYamlTest {

    @Test
    @DisplayName("운영 설정 YAML은 중복 키 없이 파싱된다")
    void applicationYamlParsesWithoutDuplicateKeys() throws Exception {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();
    }
}
