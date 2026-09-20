package com.gole.api.common.operations.sentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.sentry.*;
import io.sentry.protocol.User;
import io.sentry.transport.ITransport;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SentryErrorCollectorTest {
    @Test
    @DisplayName("실제 SDK transport envelope에서 원문 사용자·요청·예외·첨부를 제거한다")
    void capture_projectsActualSdkEnvelope() throws Exception {
        SentryOptions options = SentryErrorCollector.options("https://public@example.invalid/1");
        ITransport transport = mock(ITransport.class);
        options.setTransportFactory((ignored, request) -> transport);
        SentryClient client = new SentryClient(options);
        try {
            SentryEvent raw = new SentryEvent(new RuntimeException("SECRET_EXCEPTION"));
            raw.setLevel(SentryLevel.ERROR);
            User user = new User();
            user.setEmail("SECRET_EMAIL");
            raw.setUser(user);
            raw.setExtra("cookie", "SECRET_COOKIE");
            raw.setTag("url", "SECRET_URL");
            Hint hint = new Hint();
            hint.addAttachment(new Attachment("SECRET_ATTACHMENT".getBytes(), "SECRET_FILE"));
            client.captureEvent(raw, null, hint);
            ArgumentCaptor<SentryEnvelope> envelope = ArgumentCaptor.forClass(SentryEnvelope.class);
            verify(transport).send(envelope.capture(), any());
            StringWriter writer = new StringWriter();
            options.getSerializer().serialize(envelope.getValue(), new java.io.OutputStream() {
                @Override
                public void write(int value) {
                    writer.write(value);
                }
            });
            assertThat(writer.toString()).doesNotContain("SECRET").contains("UNEXPECTED_ERROR", "production");
            assertThat(envelope.getValue().getItems()).hasSize(1);
        } finally {
            client.close();
        }
    }

    @Test
    @DisplayName("로컬 시도 예산은 중복을 억제하고 5분 뒤 다시 허용한다")
    void capture_deduplicatesAttempts() {
        SentryClient client = mock(SentryClient.class);
        AtomicLong clock = new AtomicLong(1);
        SentryErrorCollector collector = new SentryErrorCollector(client, clock::get);
        collector.capture();
        collector.capture();
        verify(client, times(1)).captureEvent(any(SentryEvent.class));
        clock.addAndGet(300_000_000_000L);
        collector.capture();
        verify(client, times(2)).captureEvent(any(SentryEvent.class));
    }

    @Test
    @DisplayName("비활성·로컬·빈 DSN은 전송하지 않고 transport 실패는 호출자에게 전파하지 않는다")
    void capture_disablesAndIsolatesFailure() {
        new SentryErrorCollector(false, "production", "invalid").capture();
        new SentryErrorCollector(true, "local", "invalid").capture();
        new SentryErrorCollector(true, "production", "").capture();
        SentryClient client = mock(SentryClient.class);
        when(client.captureEvent(any(SentryEvent.class))).thenThrow(new IllegalStateException("SECRET"));
        SentryErrorCollector collector = new SentryErrorCollector(client, () -> 1L);
        collector.capture();
        collector.capture();
        verify(client, times(2)).captureEvent(any(SentryEvent.class));
        SentryEvent warning = new SentryEvent();
        warning.setLevel(SentryLevel.WARNING);
        assertThat(SentrySafeEvents.project(warning)).isNull();
    }

    @Test
    @DisplayName("실제 SDK HTTP transport가 로컬 수집 서버로 안전한 envelope를 보낸다")
    void capture_sendsThroughActualHttpTransport() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var received = new java.util.concurrent.CountDownLatch(1);
        var body = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/api/1/envelope/", exchange -> {
            byte[] bytes;
            try (var stream = "gzip".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))
                    ? new java.util.zip.GZIPInputStream(exchange.getRequestBody())
                    : exchange.getRequestBody()) {
                bytes = stream.readAllBytes();
            }
            body.set(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            received.countDown();
        });
        server.start();
        SentryClient client = new SentryClient(SentryErrorCollector.options(
                "http://public@127.0.0.1:" + server.getAddress().getPort() + "/1"));
        try {
            SentryEvent event = new SentryEvent(new RuntimeException("SECRET_MESSAGE"));
            event.setLevel(SentryLevel.ERROR);
            client.captureEvent(event);
            client.flush(3_000);
            assertThat(received.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(body.get()).contains("UNEXPECTED_ERROR").doesNotContain("SECRET_MESSAGE");
        } finally {
            client.close();
            server.stop(0);
        }
    }
}
