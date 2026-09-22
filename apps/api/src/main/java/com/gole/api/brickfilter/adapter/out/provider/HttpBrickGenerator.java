package com.gole.api.brickfilter.adapter.out.provider;

import com.gole.api.brickfilter.application.port.out.BrickGeneratorPort;
import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import com.gole.api.common.exception.ServiceUnavailableException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "gole.brick-filter.transport",
        havingValue = "http",
        matchIfMissing = true)
public class HttpBrickGenerator implements BrickGeneratorPort {
    private final boolean enabled;
    private final String token;
    private final URI endpoint;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public HttpBrickGenerator(
            @Value("${gole.brick-filter.enabled:false}") boolean enabled,
            // :50052 는 영속 작업자(gole.support-agent.durable.target)의 포트다. 사진 HTTP 는 :50054 를 쓴다.
            // Python 짝은 gole_brick_filter.policy.DEFAULT_HTTP_PORT 이며 함께 움직인다.
            @Value("${gole.brick-filter.endpoint:http://127.0.0.1:50054/internal/brick-filter}") URI endpoint,
            @Value("${gole.brick-filter.internal-token:}") String token) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.token = token;
        if (enabled && token.length() < 32)
            throw new IllegalArgumentException("Brick filter internal token is required");
        if (enabled
                && !("https".equals(endpoint.getScheme())
                        || ("http".equals(endpoint.getScheme())
                                && java.util.Set.of("localhost", "127.0.0.1", "[::1]")
                                        .contains(endpoint.getHost()))))
            throw new IllegalArgumentException("Brick filter endpoint requires HTTPS or loopback");
    }

    public boolean enabled() {
        return enabled;
    }

    public byte[] generate(byte[] image, Mode mode) {
        if (!enabled) throw unavailable();
        try {
            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(150))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "image/png")
                    .header("X-Brick-Mode", mode.name())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(image))
                    .build();
            var future = client.sendAsync(request, info -> new LimitedBody());
            try {
                var response = future.get(150, java.util.concurrent.TimeUnit.SECONDS);
                if (response.statusCode() != 200
                        || !response.headers()
                                .firstValue("Content-Type")
                                .orElse("")
                                .equals("image/png")) throw unavailable();
                return response.body();
            } finally {
                if (!future.isDone()) future.cancel(true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw unavailable();
        }
    }
    /** Bounds memory before buffering and makes the deadline cover the entire response body. */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result =
                new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;

        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return result;
        }

        public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
            subscription = s;
            s.request(1);
        }

        public void onNext(java.util.List<java.nio.ByteBuffer> chunks) {
            for (var chunk : chunks) {
                if ((long) bytes.size() + chunk.remaining() > 8 * 1024 * 1024) {
                    subscription.cancel();
                    result.completeExceptionally(new java.io.IOException("result size"));
                    return;
                }
                byte[] part = new byte[chunk.remaining()];
                chunk.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }

        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("BRICK_PROVIDER_UNAVAILABLE", "이미지를 만들지 못했습니다. 잠시 후 다시 시도해 주세요");
    }
}
