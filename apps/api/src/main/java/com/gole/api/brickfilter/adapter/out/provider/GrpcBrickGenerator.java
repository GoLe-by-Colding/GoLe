package com.gole.api.brickfilter.adapter.out.provider;

import com.gole.api.brick.grpc.BrickImagesGrpc;
import com.gole.api.brick.grpc.GenerateImageRequest;
import com.gole.api.brickfilter.application.port.out.BrickGeneratorPort;
import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 기존 Java 예약 원장 뒤에서만 호출한다. HTTP fallback과 자동 재시도는 하지 않는다. */
@Component
@ConditionalOnProperty(name = "gole.brick-filter.transport", havingValue = "grpc")
public class GrpcBrickGenerator implements BrickGeneratorPort {
    private final boolean enabled;
    private final ManagedChannel channel;
    private final BrickImagesGrpc.BrickImagesBlockingStub client;

    public GrpcBrickGenerator(
            @Value("${gole.brick-filter.enabled:false}") boolean enabled,
            @Value("${gole.environment:local}") String environment,
            @Value("${gole.brick-filter.grpc-target:127.0.0.1:50053}") String target,
            @Value("${gole.brick-filter.internal-token:}") String token) {
        this.enabled = enabled;
        if (!enabled) {
            channel = null;
            client = null;
            return;
        }
        if (!Set.of("local", "development", "dev", "test", "e2e").contains(environment)
                || !target.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) {
            throw new IllegalArgumentException("Brick gRPC requires local environment and loopback target");
        }
        int port = Integer.parseInt(target.substring(target.lastIndexOf(':') + 1));
        if (port < 1 || port > 65535 || !token.matches("[!-~]{32,}")) {
            throw new IllegalArgumentException("Brick gRPC requires valid port and internal token");
        }
        channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .disableRetry()
                .maxInboundMessageSize(8 * 1024 * 1024 + 1024)
                .build();
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        client = BrickImagesGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public byte[] generate(byte[] image, Mode mode) {
        if (!enabled || image == null || image.length == 0 || image.length > 4 * 1024 * 1024 || mode == null) {
            throw unavailable();
        }
        try {
            byte[] result = client.withDeadlineAfter(145, TimeUnit.SECONDS)
                    .generate(GenerateImageRequest.newBuilder()
                            .setSourcePng(ByteString.copyFrom(image))
                            .setMode(mode.name())
                            .build())
                    .getResultPng()
                    .toByteArray();
            if (result.length == 0 || result.length > 8 * 1024 * 1024) {
                throw unavailable();
            }
            // 실제 PNG와 크기는 기존 BrickImagePort가 다시 검증한다.
            return result;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("BRICK_PROVIDER_UNAVAILABLE", "이미지를 만들지 못했습니다. 잠시 후 다시 시도해 주세요");
    }

    @PreDestroy
    void close() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
