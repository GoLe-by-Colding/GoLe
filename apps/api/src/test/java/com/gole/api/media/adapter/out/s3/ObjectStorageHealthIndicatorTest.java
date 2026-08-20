package com.gole.api.media.adapter.out.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

class ObjectStorageHealthIndicatorTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final StorageProperties properties = new StorageProperties(
            new StorageProperties.S3("http://localhost:9000", "key", "secret", "us-east-1", "gole"),
            "http://localhost:8080",
            1024);

    @Test
    void reportsUpWhenBucketIsReachable() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        var health = new ObjectStorageHealthIndicator(s3Client, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", "gole");
    }

    @Test
    void reportsDownInsteadOfThrowingWhenStorageIsUnavailable() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(SdkClientException.builder()
                        .message("connection refused")
                        .build());

        var health = new ObjectStorageHealthIndicator(s3Client, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("bucket", "gole");
    }
}
