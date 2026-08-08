package com.gole.api.common.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Discord webhook 출력 어댑터. 비동기 best-effort로 동작해 사용자 요청을 지연시키지 않는다. */
@Component
public class DiscordOperationalEventPublisher implements OperationalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DiscordOperationalEventPublisher.class);
    private static final int MAX_FIELD_COUNT = 25;
    private static final int MAX_FIELD_VALUE_LENGTH = 1_024;

    private final DiscordOperationsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DiscordOperationalEventPublisher(DiscordOperationsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public void publish(OperationalEvent event) {
        String webhookUrl = properties.webhookFor(event.category());
        if (!properties.isEnabled() || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(event))))
                    .build();
            httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            log.warn("Discord 운영 알림 실패: status={}", response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Discord 운영 알림 전송 오류: {}", ex.getMessage());
                        return null;
                    });
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            log.warn("Discord 운영 알림 구성 오류: {}", ex.getMessage());
        }
    }

    private Map<String, Object> payload(OperationalEvent event) {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", event.title());
        embed.put("description", event.description());
        embed.put("color", color(event.level()));
        embed.put("timestamp", event.occurredAt().toString());
        embed.put("footer", Map.of("text", "GoLe · " + properties.getEnvironment()));

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("name", "분류", "value", event.category().name(), "inline", true));
        fields.add(Map.of("name", "심각도", "value", event.level().name(), "inline", true));
        event.fields().entrySet().stream()
                .limit(MAX_FIELD_COUNT - fields.size())
                .forEach(entry -> fields.add(Map.of(
                        "name", truncate(entry.getKey(), 256),
                        "value", truncate(entry.getValue(), MAX_FIELD_VALUE_LENGTH),
                        "inline", true)));
        embed.put("fields", fields);

        return Map.of(
                "allowed_mentions", Map.of("parse", List.of()),
                "embeds", List.of(embed));
    }

    private static int color(OperationalEvent.Level level) {
        return switch (level) {
            case INFO -> 0x5865F2;
            case SUCCESS -> 0x22C55E;
            case WARNING -> 0xFACC15;
            case ERROR -> 0xEF4444;
        };
    }

    private static String truncate(String value, int maxLength) {
        String safe = value == null || value.isBlank() ? "-" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength - 1) + "…";
    }
}
