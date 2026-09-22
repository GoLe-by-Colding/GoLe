package com.gole.api.common.operations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gole.api.common.operations.sentry.SentryErrorCollector;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SentryDiscordIntegrationTestCase {
    @Test
    @DisplayName("API는 Discord 단일 발송이고 웹 조회는 API 수집으로 되돌아가지 않는다")
    @SuppressWarnings("unchecked")
    void publish_separatesSourcesAndDeduplicates() throws Exception {
        DiscordOperationsProperties properties = new DiscordOperationsProperties();
        properties.setEnabled(true);
        properties.setEnvironment("production");
        properties.setWebhookUrl("https://discord.invalid/test");
        HttpClient http = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        DiscordOperationalEventPublisher publisher = new DiscordOperationalEventPublisher(
                properties, new ObjectMapper(), http, Duration.ofSeconds(1), Duration.ZERO, Duration.ZERO, 1);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        SentryErrorCollector collector = mock(SentryErrorCollector.class);
        publisher.setSentryCollector(collector);
        publisher.publish(new OperationalEvent(
                OperationalEvent.Category.APPLICATION,
                OperationalEvent.Level.ERROR,
                "SECRET_TITLE",
                "SECRET_MESSAGE",
                Map.of("요청 경로", "SECRET_URL", "cookie", "SECRET_COOKIE"),
                Instant.now()));
        verify(collector).capture();
        verify(http).sendAsync(any(), any(HttpResponse.BodyHandler.class));
        publisher.publishAndConfirm(webEvent());
        publisher.publishAndConfirm(webEvent());
        verifyNoMoreInteractions(collector);
        verify(http, times(1)).sendAsync(any(), any(HttpResponse.BodyHandler.class));
    }

    private OperationalEvent webEvent() {
        return new OperationalEvent(
                OperationalEvent.Category.APPLICATION,
                OperationalEvent.Level.ERROR,
                "웹 오류 수집 확인",
                "고정 진단",
                Map.of("component", "web"),
                Instant.now());
    }
}
