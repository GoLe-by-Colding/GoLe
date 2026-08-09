package com.gole.api.common.operations;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Discord webhook 출력 어댑터. 비동기 best-effort로 동작해 사용자 요청을 지연시키지 않는다. */
@Component
public class DiscordOperationalEventPublisher implements OperationalEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DiscordOperationalEventPublisher.class);
    private static final int MAX_EMBED_TEXT_LENGTH = 6_000;
    private static final int MAX_TITLE_LENGTH = 256;
    private static final int MAX_DESCRIPTION_LENGTH = 4_096;
    private static final int MAX_FIELD_COUNT = 25;
    private static final int MAX_FIELD_NAME_LENGTH = 256;
    private static final int MAX_FIELD_VALUE_LENGTH = 1_024;
    private static final int MAX_FOOTER_LENGTH = 2_048;
    private static final int SUPPRESS_NOTIFICATIONS_FLAG = 1 << 12;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(250);
    private static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final DiscordOperationsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Duration retryDelay;
    private final Duration maxRetryDelay;
    private final int maxAttempts;

    @Autowired
    public DiscordOperationalEventPublisher(DiscordOperationsProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                DEFAULT_REQUEST_TIMEOUT,
                DEFAULT_RETRY_DELAY,
                DEFAULT_MAX_RETRY_DELAY,
                DEFAULT_MAX_ATTEMPTS);
    }

    DiscordOperationalEventPublisher(
            DiscordOperationsProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            Duration requestTimeout,
            Duration retryDelay,
            Duration maxRetryDelay,
            int maxAttempts) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.retryDelay = retryDelay;
        this.maxRetryDelay = maxRetryDelay;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public void publish(OperationalEvent event) {
        String webhookUrl = properties.webhookFor(event.category());
        if (!properties.isEnabled() || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(withWaitConfirmation(webhookUrl))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload(event))))
                    .build();
            send(request, 1);
        } catch (IllegalArgumentException | JacksonException ex) {
            log.warn("Discord 운영 알림 구성 오류: {}", ex.getMessage());
        }
    }

    private void send(HttpRequest request, int attempt) {
        try {
            httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, failure) -> {
                        if (failure != null) {
                            retryOrLogFailure(request, attempt, unwrap(failure));
                            return;
                        }

                        int status = response.statusCode();
                        if (status >= 200 && status < 300) {
                            return;
                        }
                        if ((status == 429 || status >= 500) && attempt < maxAttempts) {
                            scheduleRetry(request, attempt + 1, retryDelay(response, attempt));
                            return;
                        }
                        log.warn("Discord 운영 알림 실패: status={}, attempts={}", status, attempt);
                    });
        } catch (RuntimeException ex) {
            retryOrLogFailure(request, attempt, ex);
        }
    }

    private void retryOrLogFailure(HttpRequest request, int attempt, Throwable failure) {
        if (attempt < maxAttempts) {
            scheduleRetry(request, attempt + 1, exponentialDelay(attempt));
            return;
        }
        log.warn(
                "Discord 운영 알림 전송 오류: attempts={}, error={}",
                attempt,
                failure.getClass().getSimpleName());
    }

    private void scheduleRetry(HttpRequest request, int nextAttempt, Duration delay) {
        try {
            CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                    .execute(() -> send(request, nextAttempt));
        } catch (RuntimeException ex) {
            log.warn(
                    "Discord 운영 알림 재시도 예약 실패: nextAttempt={}, error={}",
                    nextAttempt,
                    ex.getClass().getSimpleName());
        }
    }

    private Duration retryDelay(HttpResponse<?> response, int attempt) {
        if (response.statusCode() == 429) {
            Duration retryAfter = response.headers()
                    .firstValue("Retry-After")
                    .or(() -> response.headers().firstValue("X-RateLimit-Reset-After"))
                    .map(this::parseRetryAfter)
                    .orElse(null);
            if (retryAfter != null) {
                return clampDelay(retryAfter);
            }
        }
        return exponentialDelay(attempt);
    }

    private Duration parseRetryAfter(String value) {
        try {
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds) || seconds < 0) {
                return retryDelay;
            }
            return Duration.ofMillis((long) Math.ceil(seconds * 1_000));
        } catch (NumberFormatException ex) {
            return retryDelay;
        }
    }

    private Duration exponentialDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        try {
            return clampDelay(retryDelay.multipliedBy(multiplier));
        } catch (ArithmeticException ex) {
            return maxRetryDelay;
        }
    }

    private Duration clampDelay(Duration delay) {
        if (delay.isNegative()) {
            return Duration.ZERO;
        }
        return delay.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : delay;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static URI withWaitConfirmation(String webhookUrl) {
        int fragmentIndex = webhookUrl.indexOf('#');
        String base = fragmentIndex >= 0 ? webhookUrl.substring(0, fragmentIndex) : webhookUrl;
        int queryIndex = base.indexOf('?');
        String path = queryIndex >= 0 ? base.substring(0, queryIndex) : base;
        String query = queryIndex >= 0 ? base.substring(queryIndex + 1) : "";

        List<String> parameters = new ArrayList<>();
        boolean waitFound = false;
        if (!query.isBlank()) {
            for (String parameter : query.split("&")) {
                if (parameter.equals("wait") || parameter.startsWith("wait=")) {
                    if (!waitFound) {
                        parameters.add("wait=true");
                        waitFound = true;
                    }
                } else if (!parameter.isBlank()) {
                    parameters.add(parameter);
                }
            }
        }
        if (!waitFound) {
            parameters.add("wait=true");
        }
        return URI.create(path + "?" + String.join("&", parameters));
    }

    private Map<String, Object> payload(OperationalEvent event) {
        String title = truncate(event.title(), MAX_TITLE_LENGTH);
        String footer = truncate("GoLe · " + properties.getEnvironment(), MAX_FOOTER_LENGTH);
        int remaining = MAX_EMBED_TEXT_LENGTH - title.length() - footer.length();

        List<Map<String, Object>> fields = new ArrayList<>();
        remaining = addField(fields, "분류", event.category().name(), remaining);
        remaining = addField(fields, "심각도", event.level().name(), remaining);

        String description = truncate(event.description(), Math.min(MAX_DESCRIPTION_LENGTH, remaining));
        remaining -= description.length();
        for (Map.Entry<String, String> entry : event.fields().entrySet()) {
            if (fields.size() >= MAX_FIELD_COUNT || remaining < 2) {
                break;
            }
            remaining = addField(fields, entry.getKey(), entry.getValue(), remaining);
        }

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color(event.level()));
        embed.put("timestamp", event.occurredAt().toString());
        embed.put("footer", Map.of("text", footer));
        embed.put("fields", fields);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allowed_mentions", Map.of("parse", List.of()));
        if (properties.isSuppressNotifications()) {
            payload.put("flags", SUPPRESS_NOTIFICATIONS_FLAG);
        }
        if (properties.getAvatarUrl() != null && !properties.getAvatarUrl().isBlank()) {
            payload.put("avatar_url", properties.getAvatarUrl());
        }
        payload.put("embeds", List.of(embed));
        return payload;
    }

    private static int addField(
            List<Map<String, Object>> fields, String rawName, String rawValue, int remainingCharacters) {
        if (fields.size() >= MAX_FIELD_COUNT || remainingCharacters < 2) {
            return remainingCharacters;
        }
        String name = truncate(rawName, Math.min(MAX_FIELD_NAME_LENGTH, remainingCharacters - 1));
        int afterName = remainingCharacters - name.length();
        String value = truncate(rawValue, Math.min(MAX_FIELD_VALUE_LENGTH, afterName));
        fields.add(Map.of("name", name, "value", value, "inline", true));
        return afterName - value.length();
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
        if (maxLength <= 0) {
            return "";
        }
        String safe = value == null || value.isBlank() ? "-" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength - 1) + "…";
    }
}
