package com.gole.api.brickfilter.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.brick.grpc.BrickImagesGrpc;
import com.gole.api.brick.grpc.GenerateImageRequest;
import com.gole.api.brick.grpc.GenerateImageResponse;
import com.gole.api.brickfilter.application.port.out.BrickGeneratorPort;
import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GrpcBrickGeneratorTest {
    private static final String TOKEN = "test-brick-internal-token-32-characters";
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String authorization;
    private volatile String mode;
    private volatile long deadlineSeconds;
    private volatile boolean fail;
    private volatile byte[] result = new byte[] {1, 2, 3};
    private Server server;
    private GrpcBrickGenerator adapter;

    @BeforeEach
    void setup() throws Exception {
        server = ServerBuilder.forPort(0)
                .addService(new BrickImagesGrpc.BrickImagesImplBase() {
                    @Override
                    public void generate(GenerateImageRequest request, StreamObserver<GenerateImageResponse> observer) {
                        calls.incrementAndGet();
                        mode = request.getMode();
                        deadlineSeconds = Context.current().getDeadline().timeRemaining(TimeUnit.SECONDS);
                        if (fail) {
                            observer.onError(Status.UNAVAILABLE
                                    .withDescription("private-provider-error")
                                    .asRuntimeException());
                            return;
                        }
                        observer.onNext(GenerateImageResponse.newBuilder()
                                .setResultPng(ByteString.copyFrom(result))
                                .build());
                        observer.onCompleted();
                    }
                })
                .intercept(new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                        authorization = headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
                        return next.startCall(call, headers);
                    }
                })
                .build()
                .start();
        adapter = new GrpcBrickGenerator(true, "local", "127.0.0.1:" + server.getPort(), TOKEN);
    }

    @AfterEach
    void cleanup() throws Exception {
        adapter.close();
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("실제 gRPC가 내부 인증과 제한된 deadline 및 고정 모드를 전달함")
    void passesAuthenticationAndDeadline() {
        assertThat(adapter.generate(new byte[] {4}, Mode.BRICK_OBJECT)).containsExactly(1, 2, 3);
        assertThat(authorization).isEqualTo("Bearer " + TOKEN);
        assertThat(mode).isEqualTo("BRICK_OBJECT");
        assertThat(deadlineSeconds).isBetween(1L, 145L);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("provider 실패를 재시도하거나 원문 예외를 전달하지 않음")
    void failureDoesNotRetryOrLeak() {
        fail = true;
        assertThatThrownBy(() -> adapter.generate(new byte[] {4}, Mode.MINIFIGURE))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("private-provider-error");
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("빈 결과는 성공으로 반환하지 않음")
    void rejectsEmptyResult() {
        result = new byte[0];
        assertThatThrownBy(() -> adapter.generate(new byte[] {4}, Mode.MINIFIGURE))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("잘못된 입력은 원격 호출 전에 거부함")
    void rejectsInvalidInputBeforeCalling() {
        assertThatThrownBy(() -> adapter.generate(new byte[0], Mode.MINIFIGURE))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> adapter.generate(new byte[4 * 1024 * 1024 + 1], Mode.MINIFIGURE))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> adapter.generate(new byte[] {4}, null))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("운영·외부 주소·잘못된 포트·토큰은 기동 전에 거부함")
    void refusesUnsafeConfiguration() {
        assertThatThrownBy(() -> new GrpcBrickGenerator(true, "production", "127.0.0.1:50053", TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrpcBrickGenerator(true, "local", "example.com:50053", TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrpcBrickGenerator(true, "local", "127.0.0.1:0", TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrpcBrickGenerator(true, "local", "127.0.0.1:50053", "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비활성 상태는 자격증명이나 연결 없이 유지함")
    void disabledDoesNotConnect() throws Exception {
        var disabled = new GrpcBrickGenerator(false, "production", "", "");
        assertThat(disabled.enabled()).isFalse();
        assertThatThrownBy(() -> disabled.generate(new byte[] {1}, Mode.MINIFIGURE))
                .isInstanceOf(ServiceUnavailableException.class);
        disabled.close();
    }

    @Test
    @DisplayName("HTTP 기본값과 gRPC 선택 시 provider bean 하나만 생성함")
    void selectsExactlyOneTransport() {
        var context = new ApplicationContextRunner()
                .withUserConfiguration(HttpBrickGenerator.class, GrpcBrickGenerator.class);
        context.run(app -> {
            assertThat(app).hasSingleBean(BrickGeneratorPort.class);
            assertThat(app.getBean(BrickGeneratorPort.class)).isInstanceOf(HttpBrickGenerator.class);
        });
        context.withPropertyValues("gole.brick-filter.transport=grpc").run(app -> {
            assertThat(app).hasSingleBean(BrickGeneratorPort.class);
            assertThat(app.getBean(BrickGeneratorPort.class)).isInstanceOf(GrpcBrickGenerator.class);
        });
    }
}
