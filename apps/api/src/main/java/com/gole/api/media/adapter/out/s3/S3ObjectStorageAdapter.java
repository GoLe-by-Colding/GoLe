package com.gole.api.media.adapter.out.s3;

import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.domain.exception.ObjectStorageUnavailableException;
import java.util.Optional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS SDK v2 기반 S3 객체 스토리지 어댑터. MinIO(S3 호환)에 path-style로 연결한다.
 * (설계: media/adapter/out/s3)
 */
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;
    private final String bucket;
    private volatile boolean bucketReady = false;

    public S3ObjectStorageAdapter(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(b -> b.bucket(bucket));
            }
            bucketReady = true;
        } catch (SdkException e) {
            throw new ObjectStorageUnavailableException(e);
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket(); // 지연 보장: 스토리지 일시 장애 후에도 첫 업로드 시 복구
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new ObjectStorageUnavailableException(e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            String contentType = object.response().contentType();
            return Optional.of(new StoredObject(
                    object.asByteArray(), contentType == null ? "application/octet-stream" : contentType));
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new ObjectStorageUnavailableException(e);
        }
    }
}
