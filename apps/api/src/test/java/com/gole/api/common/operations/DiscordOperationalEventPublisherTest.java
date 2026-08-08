package com.gole.api.common.operations;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DiscordOperationalEventPublisherTest {

    @Test
    void publish_sendsStructuredDiscordPayload_withoutMentions() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            received.countDown();
        });
        server.start();

        try {
            DiscordOperationsProperties properties = new DiscordOperationsProperties();
            properties.setEnabled(true);
            properties.setEnvironment("test");
            properties.setWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            DiscordOperationalEventPublisher publisher =
                    new DiscordOperationalEventPublisher(properties, new ObjectMapper());

            publisher.publish(new OperationalEvent(
                    Category.PAYMENT,
                    Level.SUCCESS,
                    "결제 완료",
                    "테스트 이벤트",
                    Map.of("주문 ID", "order-1"),
                    Instant.parse("2026-01-01T00:00:00Z")));

            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(body.get())
                    .contains("결제 완료", "order-1", "GoLe · test")
                    .contains("\"allowed_mentions\":{\"parse\":[]}");
        } finally {
            server.stop(0);
        }
    }
}
