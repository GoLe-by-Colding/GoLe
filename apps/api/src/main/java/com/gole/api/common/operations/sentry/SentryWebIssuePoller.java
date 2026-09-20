package com.gole.api.common.operations.sentry;

import com.gole.api.common.operations.ConfirmedOperationalEventPublisher;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher.DeliveryStatus;
import com.gole.api.common.operations.OperationalEvent;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 조회 진행점과 전송 원장을 분리하며 Discord 수락 전에는 구간을 완료하지 않는다. */
@Component
public class SentryWebIssuePoller {
    private static final String ENDPOINT = "https://sentry.io/api/0/organizations/gole-j9/issues/";
    private final boolean enabled;
    private final String token;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final ConfirmedOperationalEventPublisher publisher;
    private final SentryPollStore store;
    private final Clock clock;
    private int failures;
    private volatile State state = State.DISABLED;

    public enum State {
        DISABLED,
        BUSY,
        READ_FAILED,
        DELIVERY_PENDING,
        COLLECTED_AND_DELIVERED,
        NO_RECENT_ERROR,
        STORAGE_FAILED
    }

    @Autowired
    public SentryWebIssuePoller(
            @Value("${gole.sentry.poll-enabled:false}") boolean enabled,
            @Value("${gole.sentry.environment:local}") String environment,
            @Value("${gole.sentry.read-token:}") String token,
            ObjectMapper mapper,
            ConfirmedOperationalEventPublisher publisher,
            SentryPollStore store) {
        this(
                enabled && "production".equals(environment) && !token.isBlank(),
                token,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                mapper,
                publisher,
                store,
                Clock.systemUTC());
    }

    SentryWebIssuePoller(
            boolean enabled,
            String token,
            HttpClient client,
            ObjectMapper mapper,
            ConfirmedOperationalEventPublisher publisher,
            SentryPollStore store,
            Clock clock) {
        this.enabled = enabled;
        this.token = token;
        this.client = client;
        this.mapper = mapper;
        this.publisher = publisher;
        this.store = store;
        this.clock = clock;
    }

    public State state() {
        return state;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public synchronized void poll() {
        if (!enabled) {
            state = State.DISABLED;
            return;
        }
        SentryPollStore.Lease lease = null;
        try {
            Instant now = clock.instant();
            lease = store.acquire(now);
            if (lease == null) {
                state = State.BUSY;
                return;
            }
            SentryPollStore.Scan scan = lease.scan();
            if (scan.until() == null) {
                scan = new SentryPollStore.Scan(scan.from(), now, null, false);
                store.saveScan(lease, scan);
            }
            if (!scan.readComplete() && !now.isBefore(lease.readNotBefore())) {
                scan = readPage(lease, scan);
            } else if (!scan.readComplete()) state = State.READ_FAILED;
            // 조회 장애 중에도 이미 저장한 전송 원장은 재시도한다.
            boolean delivered = deliverPending(now);
            if (scan.readComplete() && !store.hasPending()) {
                // 1분 중첩은 지연 수집을 다시 읽되 원장 키로 중복 발송을 막는다.
                Instant nextFrom = scan.until().minusSeconds(60);
                if (nextFrom.isBefore(scan.from())) nextFrom = scan.from();
                store.saveScan(lease, new SentryPollStore.Scan(nextFrom, null, null, false));
                state = delivered ? State.COLLECTED_AND_DELIVERED : State.NO_RECENT_ERROR;
            } else if (state != State.READ_FAILED) state = State.DELIVERY_PENDING;
        } catch (RuntimeException storageFailure) {
            state = State.STORAGE_FAILED;
        } finally {
            if (lease != null) {
                try {
                    store.release(lease);
                } catch (RuntimeException failure) {
                    state = State.STORAGE_FAILED;
                }
            }
        }
    }

    private SentryPollStore.Scan readPage(SentryPollStore.Lease lease, SentryPollStore.Scan scan) {
        try {
            String query =
                    "?project=gole-web&environment=production&query=level%3A%5Berror%2Cfatal%5D&sort=date&limit=100"
                            + "&start=" + encode(scan.from().toString()) + "&end="
                            + encode(scan.until().toString())
                            + (scan.cursor() == null ? "" : "&cursor=" + encode(scan.cursor()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + query))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                readFailed(
                        lease,
                        response.statusCode() == 429
                                ? response.headers().firstValue("Retry-After").orElse(null)
                                : null);
                return scan;
            }
            if (response.body().length() > 2_000_000) {
                readFailed(lease, null);
                return scan;
            }
            JsonNode issues = mapper.readTree(response.body());
            if (!issues.isArray() || issues.size() > 100) {
                readFailed(lease, null);
                return scan;
            }
            for (JsonNode issue : issues) {
                String level = issue.path("level").asString();
                String id = issue.path("id").asString();
                if ((!"error".equals(level) && !"fatal".equals(level))
                        || !"gole-web".equals(issue.path("project").path("slug").asString())
                        || id == null
                        || !id.matches("[0-9]{1,30}")) {
                    readFailed(lease, null);
                    return scan;
                }
                String filtered = issue.path("filtered").path("lastSeen").asString();
                Instant seen = Instant.parse(
                        filtered == null || filtered.isBlank()
                                ? issue.path("lastSeen").asString()
                                : filtered);
                String material = id + ":" + Math.floorDiv(seen.getEpochSecond(), 300);
                String key = HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
                // 커서 저장 전에 원장을 upsert하므로 중단 후 페이지 재조회에 멱등이다.
                store.enqueue(key, seen);
            }
            String cursor = nextCursor(response.headers().firstValue("Link").orElse(""));
            SentryPollStore.Scan advanced = new SentryPollStore.Scan(scan.from(), scan.until(), cursor, cursor == null);
            store.saveScan(lease, advanced);
            failures = 0;
            state = State.DELIVERY_PENDING;
            return advanced;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            readFailed(lease, null);
            return scan;
        } catch (Exception failure) {
            // 원문 응답·URI·토큰·예외 문자열은 기록하지 않는다. 진행점은 그대로 남는다.
            readFailed(lease, null);
            return scan;
        }
    }

    private boolean deliverPending(Instant now) {
        boolean delivered = false;
        for (SentryPollStore.Alert alert : store.pending(now)) {
            OperationalEvent event = new OperationalEvent(
                    OperationalEvent.Category.APPLICATION,
                    OperationalEvent.Level.ERROR,
                    "웹 오류 수집 확인",
                    "Sentry에서 production 웹 오류가 조회되었습니다.",
                    Map.of("component", "web", "diagnostic", "UNEXPECTED_ERROR"),
                    alert.occurredAt());
            var result = publisher.publishAndConfirm(event);
            if (result.status() == DeliveryStatus.DELIVERED) {
                store.delivered(alert.id());
                delivered = true;
            } else {
                long seconds = Math.min(900, 60L << Math.min(alert.attempts(), 4));
                if (result.retryAfter() != null)
                    seconds = Math.max(seconds, result.retryAfter().toSeconds());
                store.retry(alert.id(), now.plusSeconds(Math.min(86_400, seconds)));
                break; // 한 채널의 실패/제한 응답 뒤 같은 tick에서 요청을 쏟지 않는다.
            }
        }
        return delivered;
    }

    static String nextCursor(String link) {
        for (String part : link.split(",")) {
            if (!part.contains("rel=\"next\"") || !part.contains("results=\"true\"")) continue;
            var matcher = Pattern.compile("cursor=\"([A-Za-z0-9:_=-]{1,512})\"").matcher(part);
            if (!matcher.find()) throw new IllegalArgumentException("SENTRY_CURSOR_INVALID");
            return matcher.group(1);
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void readFailed(SentryPollStore.Lease lease, String retryAfter) {
        state = State.READ_FAILED;
        long delay = Math.min(900, 60L << Math.min(failures++, 4));
        if (retryAfter != null) {
            try {
                double seconds = Double.parseDouble(retryAfter);
                if (Double.isFinite(seconds) && seconds >= 0) delay = Math.max(delay, (long) Math.min(86_400, seconds));
            } catch (NumberFormatException ignored) {
                /* 자유 텍스트 헤더는 기록하지 않는다. */
            }
        }
        store.deferRead(lease, clock.instant().plusSeconds(delay));
    }
}
