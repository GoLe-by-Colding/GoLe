package com.gole.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebCorsConfigTest {

    @Test
    void exposesRetryAfterSoTheBrowserCanHonorRateLimitBackoff() {
        var registry = new InspectableCorsRegistry();
        new WebCorsConfig(new String[] {"http://localhost:3000", "http://localhost:3010"}).addCorsMappings(registry);

        CorsConfiguration api = registry.configurations().get("/api/**");

        assertThat(api).isNotNull();
        assertThat(api.getExposedHeaders()).contains(HttpHeaders.RETRY_AFTER);
        assertThat(api.getAllowedOrigins()).containsExactly("http://localhost:3000", "http://localhost:3010");
        assertThat(api.getAllowedOrigins()).doesNotContain("*");
        assertThat(api.getAllowCredentials()).isTrue();
    }

    private static final class InspectableCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
