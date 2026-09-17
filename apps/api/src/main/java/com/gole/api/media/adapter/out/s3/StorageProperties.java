package com.gole.api.media.adapter.out.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 객체 스토리지/공개 URL 설정. (설계: storage.*)
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        S3 s3,
        String publicBaseUrl,
        long maxImageBytes,
        int maxImageWidth,
        int maxImageHeight,
        long maxImagePixels,
        String heifPython,
        java.time.Duration heifTimeout) {

    public StorageProperties(
            S3 s3,
            String publicBaseUrl,
            long maxImageBytes,
            int maxImageWidth,
            int maxImageHeight,
            long maxImagePixels) {
        this(s3, publicBaseUrl, maxImageBytes, maxImageWidth, maxImageHeight, maxImagePixels, null, null);
    }

    @ConstructorBinding
    public StorageProperties {
        if (heifPython == null || heifPython.isBlank()) heifPython = "/opt/heif/bin/python";
        if (heifTimeout == null) heifTimeout = java.time.Duration.ofSeconds(10);
        if (heifTimeout.isNegative()
                || heifTimeout.isZero()
                || heifTimeout.compareTo(java.time.Duration.ofSeconds(15)) > 0)
            throw new IllegalArgumentException("HEIF timeout must be positive and at most 15 seconds");
        if (maxImageBytes <= 0) {
            maxImageBytes = 5_242_880L; // 5MB 기본값
        }
        if (maxImageWidth <= 0) {
            maxImageWidth = 8_192;
        }
        if (maxImageHeight <= 0) {
            maxImageHeight = 8_192;
        }
        if (maxImagePixels <= 0) {
            maxImagePixels = 16_000_000L;
        }
    }

    public record S3(String endpoint, String accessKey, String secretKey, String region, String bucket) {}
}
