package com.gole.api.media.adapter.out.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 객체 스토리지/공개 URL 설정. (설계: storage.*)
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        S3 s3,
        String publicBaseUrl,
        long maxImageBytes) {

    public StorageProperties {
        if (maxImageBytes <= 0) {
            maxImageBytes = 5_242_880L; // 5MB 기본값
        }
    }

    public record S3(
            String endpoint,
            String accessKey,
            String secretKey,
            String region,
            String bucket) {}
}
