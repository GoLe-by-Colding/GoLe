package com.gole.api.brickfilter.adapter.out.provider;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gole.api.brickfilter.domain.model.BrickJob.Mode;
import com.gole.api.common.exception.ServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class HttpBrickGeneratorTest {
    @Test
    void disabledAndNonPrivateHttpEndpointsFailClosed() {
        var adapter = new HttpBrickGenerator(false, URI.create("http://127.0.0.1:50054/internal/brick-filter"), "");
        assertThat(adapter.enabled()).isFalse();
        assertThatThrownBy(() -> adapter.generate(new byte[] {1}, Mode.MINIFIGURE))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> new HttpBrickGenerator(
                        true, URI.create("http://remote.example/internal/brick-filter"), "t".repeat(32)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateBinaryHttpContractMatchesPythonHandler() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new java.util.concurrent.atomic.AtomicReference<byte[]>();
        var mode = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/internal/brick-filter", exchange -> {
            if (!exchange.getRequestHeaders().getFirst("Authorization").equals("Bearer " + "t".repeat(32)))
                throw new AssertionError("auth");
            if (!exchange.getRequestHeaders().getFirst("Content-Type").equals("image/png"))
                throw new AssertionError("mime");
            received.set(exchange.getRequestBody().readAllBytes());
            mode.set(exchange.getRequestHeaders().getFirst("X-Brick-Mode"));
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 3);
            exchange.getResponseBody().write(new byte[] {4, 5, 6});
            exchange.close();
        });
        server.start();
        try {
            var adapter = new HttpBrickGenerator(
                    true,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/brick-filter"),
                    "t".repeat(32));
            assertThat(adapter.generate(new byte[] {1, 2, 3}, Mode.BRICK_OBJECT))
                    .containsExactly(4, 5, 6);
            assertThat(received.get()).containsExactly(1, 2, 3);
            assertThat(mode.get()).isEqualTo("BRICK_OBJECT");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void responseBufferIsBoundedBeforeAllocation() {
        var body = new HttpBrickGenerator.LimitedBody();
        var subscription = mock(Flow.Subscription.class);
        body.onSubscribe(subscription);
        body.onNext(java.util.List.of(java.nio.ByteBuffer.allocate(8 * 1024 * 1024 + 1)));
        assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();
        verify(subscription).cancel();
    }
}
