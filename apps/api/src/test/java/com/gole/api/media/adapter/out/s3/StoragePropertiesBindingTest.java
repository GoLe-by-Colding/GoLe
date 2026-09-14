package com.gole.api.media.adapter.out.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StoragePropertiesBindingTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class);

    @Test
    @DisplayName("호환 생성자가 있어도 정식 생성자로 스토리지 설정을 바인딩한다")
    void bind_usesCanonicalConstructor() {
        runner.withPropertyValues(
                        "storage.s3.endpoint=http://localhost:9000",
                        "storage.s3.bucket=test-images",
                        "storage.public-base-url=http://localhost:8090",
                        "storage.max-image-bytes=123456",
                        "storage.heif-python=/test/python",
                        "storage.heif-timeout=PT3S")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(StorageProperties.class);
                    StorageProperties properties = context.getBean(StorageProperties.class);
                    assertThat(properties.s3().bucket()).isEqualTo("test-images");
                    assertThat(properties.publicBaseUrl()).isEqualTo("http://localhost:8090");
                    assertThat(properties.maxImageBytes()).isEqualTo(123456L);
                    assertThat(properties.heifPython()).isEqualTo("/test/python");
                    assertThat(properties.heifTimeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    @Test
    @DisplayName("생략한 이미지 제한과 HEIF 설정은 안전한 기본값을 유지한다")
    void bind_preservesDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            StorageProperties properties = context.getBean(StorageProperties.class);
            assertThat(properties.maxImageBytes()).isEqualTo(5_242_880L);
            assertThat(properties.heifPython()).isEqualTo("/opt/heif/bin/python");
            assertThat(properties.heifTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    @DisplayName("HEIF 제한 시간을 넘긴 설정은 기동 시 거절한다")
    void bind_rejectsInvalidTimeout() {
        runner.withPropertyValues("storage.heif-timeout=PT16S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    static class BindingConfiguration {}
}
