package com.gole.api.media.adapter.out.s3;

import com.gole.api.media.application.port.out.ObjectStoragePort;
import com.gole.api.media.application.service.MediaService;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * 미디어 컨텍스트 조립(Composition Root). S3Client(MinIO) 및 포트/서비스 빈을 구성한다.
 * application 레이어(MediaService)를 프레임워크로부터 분리하기 위해 빈 등록은 여기서 한다.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        return S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                // MinIO는 path-style 접근 필수
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public ObjectStoragePort objectStoragePort(S3Client s3Client, StorageProperties properties) {
        S3ObjectStorageAdapter adapter =
                new S3ObjectStorageAdapter(s3Client, properties.s3().bucket());
        adapter.ensureBucket(); // 요구사항 M1.5: 버킷 보장
        return adapter;
    }

    @Bean
    public MediaService mediaService(ObjectStoragePort objectStorage, StorageProperties properties) {
        return new MediaService(objectStorage, properties.publicBaseUrl(), properties.maxImageBytes());
    }
}
