package com.gole.api.common.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gole.api.common.operations.OperationalEvent.Category;
import com.gole.api.common.operations.OperationalEvent.Level;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class DiscordOperationalEventPublisherTest {

    @Test
    void publish_routesEveryOperationalCategoryToItsRoleSpecificWebhook() throws Exception {
        CountDownLatch received = new CountDownLatch(5);
        ConcurrentLinkedQueue<String> paths = new ConcurrentLinkedQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (String path : List.of("/account", "/payment", "/support", "/operations")) {
            server.createContext(path, exchange -> {
                paths.add(exchange.getRequestURI().getPath());
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                received.countDown();
            });
        }
        server.start();

        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            DiscordOperationsProperties properties = new DiscordOperationsProperties();
            properties.setEnabled(true);
            properties.setAccountWebhookUrl(base + "/account");
            properties.setPaymentWebhookUrl(base + "/payment");
            properties.setSupportWebhookUrl(base + "/support");
            properties.setOperationsWebhookUrl(base + "/operations");
            DiscordOperationalEventPublisher publisher =
                    new DiscordOperationalEventPublisher(properties, new ObjectMapper());

            for (Category category : Category.values()) {
                publisher.publish(new OperationalEvent(
                        category,
                        Level.INFO,
                        "라우팅 확인",
                        "역할별 목적지를 확인합니다.",
                        Map.of(),
                        Instant.parse("2026-01-01T00:00:00Z")));
            }

            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(paths)
                    .containsExactlyInAnyOrder("/account", "/payment", "/support", "/operations", "/operations");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publish_sendsStructuredDiscordPayload_withoutMentions() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
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
            properties.setWebhookUrl(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook?thread_id=123&wait=false");
            properties.setAvatarUrl("https://gole.example/icon.svg");
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
            assertThat(query.get()).isEqualTo("thread_id=123&wait=true");
            assertThat(body.get())
                    .contains("결제 완료", "order-1", "GoLe · test")
                    .contains("\"avatar_url\":\"https://gole.example/icon.svg\"")
                    .contains("\"allowed_mentions\":{\"parse\":[]}")
                    .contains("\"flags\":4096")
                    .doesNotContain("\"username\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publish_canEnableDiscordNotificationsByConfiguration() throws Exception {
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
            DiscordOperationsProperties properties = properties(server);
            properties.setSuppressNotifications(false);
            DiscordOperationalEventPublisher publisher =
                    new DiscordOperationalEventPublisher(properties, new ObjectMapper());

            publisher.publish(event(Map.of()));

            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(body.get())
                    .contains("\"allowed_mentions\":{\"parse\":[]}")
                    .doesNotContain("\"flags\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publish_deduplicatesApplicationEventWhileInFlightAndAfterSuccessfulDelivery() {
        HttpClient httpClient = mock(HttpClient.class);
        CompletableFuture<HttpResponse<Void>> delivery = new CompletableFuture<>();
        when(httpClient.sendAsync(
                        any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(delivery);
        DiscordOperationalEventPublisher publisher = testPublisher(
                applicationProperties(),
                httpClient,
                Duration.ofSeconds(1),
                Duration.ofMillis(1),
                Duration.ofMillis(10),
                1);
        OperationalEvent first = applicationEvent("/api/v1/media/catalog/10294.svg");
        OperationalEvent sameFailureFromAnotherRequest = applicationEvent("/api/v1/media/catalog/10307.svg");

        publisher.publish(first);
        publisher.publish(sameFailureFromAnotherRequest);

        verify(httpClient, times(1))
                .sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());

        delivery.complete(response(204));
        publisher.publish(sameFailureFromAnotherRequest);

        verify(httpClient, times(1))
                .sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
    }

    @Test
    void publish_releasesApplicationDeduplicationAfterFinalFailure() {
        HttpClient httpClient = mock(HttpClient.class);
        CompletableFuture<HttpResponse<Void>> failedDelivery = new CompletableFuture<>();
        CompletableFuture<HttpResponse<Void>> nextDelivery = new CompletableFuture<>();
        when(httpClient.sendAsync(
                        any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(failedDelivery)
                .thenReturn(nextDelivery);
        DiscordOperationalEventPublisher publisher = testPublisher(
                applicationProperties(),
                httpClient,
                Duration.ofSeconds(1),
                Duration.ofMillis(1),
                Duration.ofMillis(10),
                1);
        OperationalEvent event = applicationEvent("/api/v1/media/catalog/10294.svg");

        publisher.publish(event);
        failedDelivery.complete(response(503));
        publisher.publish(event);

        verify(httpClient, times(2))
                .sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        nextDelivery.complete(response(204));
    }

    @Test
    void publish_retriesRateLimitUsingRetryAfter() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch succeeded = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
            } else {
                exchange.sendResponseHeaders(204, -1);
                succeeded.countDown();
            }
            exchange.close();
        });
        server.start();

        try {
            DiscordOperationalEventPublisher publisher = testPublisher(
                    properties(server), Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMillis(10), 3);

            publisher.publish(event(Map.of()));

            assertThat(succeeded.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publish_retriesServerErrorsOnlyUpToConfiguredMaximum() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch threeAttempts = new CountDownLatch(3);
        CountDownLatch fourthAttempt = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            if (requests.incrementAndGet() > 3) {
                fourthAttempt.countDown();
            }
            threeAttempts.countDown();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        try {
            DiscordOperationalEventPublisher publisher = testPublisher(
                    properties(server), Duration.ofSeconds(1), Duration.ofMillis(1), Duration.ofMillis(10), 3);

            publisher.publish(event(Map.of()));

            assertThat(threeAttempts.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fourthAttempt.await(150, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(requests).hasValue(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publish_retriesTimeoutWithoutBlockingRetryThreads() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch threeAttempts = new CountDownLatch(3);
        CountDownLatch fourthAttempt = new CountDownLatch(1);
        ExecutorService serverExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "discord-timeout-test");
            thread.setDaemon(true);
            return thread;
        });
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        server.createContext("/webhook", exchange -> {
            if (requests.incrementAndGet() > 3) {
                fourthAttempt.countDown();
            }
            threeAttempts.countDown();
            try {
                Thread.sleep(150);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            DiscordOperationalEventPublisher publisher = testPublisher(
                    properties(server), Duration.ofMillis(20), Duration.ofMillis(1), Duration.ofMillis(10), 3);

            publisher.publish(event(Map.of()));

            assertThat(threeAttempts.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fourthAttempt.await(250, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(requests).hasValue(3);
        } finally {
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void publish_truncatesEmbedTextAndFieldsToDiscordLimits() throws Exception {
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
            DiscordOperationsProperties properties = properties(server);
            Map<String, String> oversizedFields = new LinkedHashMap<>();
            for (int index = 0; index < 30; index++) {
                oversizedFields.put("n".repeat(300) + index, "v".repeat(1_500));
            }
            DiscordOperationalEventPublisher publisher =
                    new DiscordOperationalEventPublisher(properties, new ObjectMapper());

            publisher.publish(new OperationalEvent(
                    Category.PAYMENT,
                    Level.ERROR,
                    "t".repeat(500),
                    "d".repeat(5_000),
                    oversizedFields,
                    Instant.parse("2026-01-01T00:00:00Z")));

            assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
            Map<String, Object> payload = new ObjectMapper().readValue(body.get(), new TypeReference<>() {});
            Map<String, Object> embed = asMap(asList(payload.get("embeds")).getFirst());
            String title = String.valueOf(embed.get("title"));
            String description = String.valueOf(embed.get("description"));
            String footer = String.valueOf(asMap(embed.get("footer")).get("text"));
            List<?> fields = asList(embed.get("fields"));

            assertThat(title).hasSizeLessThanOrEqualTo(256);
            assertThat(description).hasSizeLessThanOrEqualTo(4_096);
            assertThat(footer).hasSizeLessThanOrEqualTo(2_048);
            assertThat(fields).hasSizeLessThanOrEqualTo(25);
            assertThat(fields.size()).isGreaterThan(2);

            int totalTextLength = title.length() + description.length() + footer.length();
            for (Object rawField : fields) {
                Map<String, Object> field = asMap(rawField);
                String name = String.valueOf(field.get("name"));
                String value = String.valueOf(field.get("value"));
                assertThat(name).hasSizeLessThanOrEqualTo(256);
                assertThat(value).hasSizeLessThanOrEqualTo(1_024);
                totalTextLength += name.length() + value.length();
            }
            assertThat(totalTextLength).isLessThanOrEqualTo(6_000);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publishAndConfirmReturnsOnlyAfterDiscordAcceptanceAndKeepsMentionsDisabled() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            DiscordOperationalEventPublisher publisher =
                    new DiscordOperationalEventPublisher(properties(server), new ObjectMapper());
            OperationalEvent support = new OperationalEvent(
                    Category.SUPPORT,
                    Level.INFO,
                    "새 운영 문의 접수",
                    "관리자 문의함에서 확인해 주세요.",
                    Map.of("이벤트 ID", "event-1"),
                    Instant.parse("2026-09-04T01:02:03Z"));

            var result = publisher.publishAndConfirm(support);

            assertThat(result.status()).isEqualTo(ConfirmedOperationalEventPublisher.DeliveryStatus.DELIVERED);
            assertThat(body.get())
                    .contains("event-1", "\"allowed_mentions\":{\"parse\":[]}")
                    .doesNotContain("@everyone", "@here");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publishAndConfirmClassifiesServerAndClientFailuresForDurableWorker() throws Exception {
        AtomicInteger status = new AtomicInteger(503);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();

        try {
            DiscordOperationalEventPublisher publisher =
                    new DiscordOperationalEventPublisher(properties(server), new ObjectMapper());

            var retryable = publisher.publishAndConfirm(event(Map.of("이벤트 ID", "event-1")));
            status.set(400);
            var permanent = publisher.publishAndConfirm(event(Map.of("이벤트 ID", "event-2")));

            assertThat(retryable.status())
                    .isEqualTo(ConfirmedOperationalEventPublisher.DeliveryStatus.RETRYABLE_FAILURE);
            assertThat(retryable.errorCode()).isEqualTo("HTTP_503");
            assertThat(permanent.status())
                    .isEqualTo(ConfirmedOperationalEventPublisher.DeliveryStatus.PERMANENT_FAILURE);
            assertThat(permanent.errorCode()).isEqualTo("HTTP_400");
        } finally {
            server.stop(0);
        }
    }

    private static DiscordOperationsProperties properties(HttpServer server) {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setEnvironment("test");
        properties.setWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
        return properties;
    }

    private static DiscordOperationalEventPublisher testPublisher(
            DiscordOperationsProperties properties,
            Duration requestTimeout,
            Duration retryDelay,
            Duration maxRetryDelay,
            int maxAttempts) {
        return new DiscordOperationalEventPublisher(
                properties,
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                requestTimeout,
                retryDelay,
                maxRetryDelay,
                maxAttempts);
    }

    private static DiscordOperationalEventPublisher testPublisher(
            DiscordOperationsProperties properties,
            HttpClient httpClient,
            Duration requestTimeout,
            Duration retryDelay,
            Duration maxRetryDelay,
            int maxAttempts) {
        return new DiscordOperationalEventPublisher(
                properties, new ObjectMapper(), httpClient, requestTimeout, retryDelay, maxRetryDelay, maxAttempts);
    }

    private static DiscordOperationsProperties applicationProperties() {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setEnvironment("test");
        properties.setOperationsWebhookUrl("https://discord.com/api/webhooks/1/test-token");
        properties.setDeduplicationWindow(Duration.ofMinutes(5));
        return properties;
    }

    private static OperationalEvent applicationEvent(String requestPath) {
        return new OperationalEvent(
                Category.APPLICATION,
                Level.ERROR,
                "미디어 저장소 연결 장애",
                "스토리지 연결 실패",
                Map.of("요청 경로", requestPath, "예외 종류", "SdkClientException"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Void> response(int status) {
        HttpResponse<Void> response = (HttpResponse<Void>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }

    private static OperationalEvent event(Map<String, String> fields) {
        return new OperationalEvent(
                Category.PAYMENT, Level.SUCCESS, "결제 완료", "테스트 이벤트", fields, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<?> asList(Object value) {
        return (List<?>) value;
    }
}
