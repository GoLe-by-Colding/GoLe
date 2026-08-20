package com.gole.api.media.adapter.out.s3;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** 업로드 저장소가 실제로 요청을 받을 수 있는지 Actuator 전체 헬스에 반영한다. */
@Component("objectStorageHealthIndicator")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final String bucket;

    public ObjectStorageHealthIndicator(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.s3().bucket();
    }

    @Override
    public Health health() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return Health.up().withDetail("bucket", bucket).build();
        } catch (RuntimeException ex) {
            return Health.down(ex).withDetail("bucket", bucket).build();
        }
    }
}
