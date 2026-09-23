package com.gole.api.common.operations.sentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gole.api.common.operations.ConfirmedOperationalEventPublisher;
import com.gole.api.common.operations.ConfirmedOperationalEventPublisher.DeliveryResult;
import com.gole.api.common.operations.OperationalEvent;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SentryWebIssuePollerTest {
    private final HttpClient http = mock(HttpClient.class);
    private final ConfirmedOperationalEventPublisher discord = mock(ConfirmedOperationalEventPublisher.class);
    private final MemoryStore store = new MemoryStore();
    private Instant now = Instant.parse("2026-09-12T00:00:00Z");

    private SentryWebIssuePoller worker() {
        return new SentryWebIssuePoller(
                true, "test-token", http, new ObjectMapper(), discord, store, Clock.fixed(now, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (a, b) -> true));
        return response;
    }

    private String issue(String id) {
        return "[{\"id\":\"" + id + "\",\"level\":\"error\",\"project\":{\"slug\":\"gole-web\"},"
                + "\"lastSeen\":\"2026-09-11T23:59:00Z\",\"title\":\"SECRET\",\"user\":\"SECRET\",\"url\":\"SECRET\"}]";
    }

    @Test
    @DisplayName("다중 페이지는 cursor로 재시작 복구하고 원문 없이 발송한 뒤 watermark를 확정한다")
    @SuppressWarnings("unchecked")
    void poll_recoversPaginationAndProjectsPayload() throws Exception {
        doReturn(
                        response(
                                200,
                                issue("1"),
                                Map.of(
                                        "Link",
                                        List.of(
                                                "<https://ignored.invalid>; rel=\"next\"; results=\"true\"; cursor=\"123:0:0\""))),
                        response(200, issue("2"), Map.of()))
                .when(http)
                .send(any(), any(HttpResponse.BodyHandler.class));
        when(discord.publishAndConfirm(any())).thenReturn(DeliveryResult.delivered());
        Instant original = store.scan.from();
        worker().poll();
        assertThat(store.scan.cursor()).isEqualTo("123:0:0");
        assertThat(store.scan.from()).isEqualTo(original);
        worker().poll(); // 새 객체가 같은 원장에서 이어 읽는다.
        assertThat(store.scan.until()).isNull();
        assertThat(store.scan.from()).isEqualTo(now.minusSeconds(60));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requests.getAllValues().get(1).uri().toString())
                .contains("cursor=123%3A0%3A0", "environment=production", "project=gole-web", "limit=100");
        ArgumentCaptor<OperationalEvent> events = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(discord, times(2)).publishAndConfirm(events.capture());
        assertThat(events.getAllValues().toString()).doesNotContain("SECRET", "ignored.invalid", "test-token");
        assertThat(store.rows).hasSize(2);
    }

    @Test
    @DisplayName("Discord 실패·재시작은 pending을 보존하고 수락 전 watermark가 전진하지 않는다")
    @SuppressWarnings("unchecked")
    void poll_retriesDurablyWithoutAdvancingWatermark() throws Exception {
        doReturn(response(200, issue("1"), Map.of())).when(http).send(any(), any(HttpResponse.BodyHandler.class));
        when(discord.publishAndConfirm(any()))
                .thenReturn(DeliveryResult.retryable("HTTP_500", Duration.ofSeconds(60)), DeliveryResult.delivered());
        Instant original = store.scan.from();
        worker().poll();
        assertThat(store.scan.readComplete()).isTrue();
        assertThat(store.scan.from()).isEqualTo(original);
        worker().poll();
        verify(discord, times(1)).publishAndConfirm(any());
        now = now.plusSeconds(3600);
        worker().poll();
        verify(discord, times(2)).publishAndConfirm(any());
        verify(http, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
        assertThat(store.hasPending()).isFalse();
        assertThat(store.scan.until()).isNull();
    }

    @Test
    @DisplayName("페이지 진행점 저장 실패 뒤 재조회는 원장 키로 중복 발송을 막는다")
    @SuppressWarnings("unchecked")
    void poll_replaysPageIdempotently() throws Exception {
        doReturn(response(200, issue("1"), Map.of())).when(http).send(any(), any(HttpResponse.BodyHandler.class));
        when(discord.publishAndConfirm(any())).thenReturn(DeliveryResult.delivered());
        store.failPageSave = true;
        worker().poll();
        now = now.plusSeconds(120);
        worker().poll();
        assertThat(store.rows).hasSize(1);
        verify(discord, times(1)).publishAndConfirm(any());
    }

    @Test
    @DisplayName("429 Retry-After는 재시작 후에도 유지되고 오류 조회는 성공이 아니다")
    @SuppressWarnings("unchecked")
    void poll_persistsRateLimitAcrossRestart() throws Exception {
        doReturn(response(429, "SECRET", Map.of("Retry-After", List.of("180"))))
                .when(http)
                .send(any(), any(HttpResponse.BodyHandler.class));
        SentryWebIssuePoller first = worker();
        first.poll();
        assertThat(first.state()).isEqualTo(SentryWebIssuePoller.State.READ_FAILED);
        now = now.plusSeconds(60);
        worker().poll();
        verify(http, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
        now = now.plusSeconds(120);
        worker().poll();
        verify(http, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
        verifyNoInteractions(discord);
        assertThat(store.scan.readComplete()).isFalse();
    }

    @Test
    @DisplayName("비활성·local은 DB와 외부 연결 모두 사용하지 않는다")
    void poll_disablesAllSideEffects() {
        SentryPollStore unused = mock(SentryPollStore.class);
        SentryWebIssuePoller disabled =
                new SentryWebIssuePoller(false, "", http, new ObjectMapper(), discord, unused, Clock.systemUTC());
        disabled.poll();
        assertThat(disabled.state()).isEqualTo(SentryWebIssuePoller.State.DISABLED);
        new SentryWebIssuePoller(true, "local", "test-token", new ObjectMapper(), discord, unused).poll();
        verifyNoInteractions(http, discord, unused);
    }

    @Test
    @DisplayName("잘못된 JSON과 다른 프로젝트는 원장·발송·watermark를 만들지 않는다")
    @SuppressWarnings("unchecked")
    void poll_rejectsMalformedOrWrongProject() throws Exception {
        doReturn(response(200, issue("1").replace("gole-web", "gole-api"), Map.of()))
                .when(http)
                .send(any(), any(HttpResponse.BodyHandler.class));
        worker().poll();
        assertThat(store.rows).isEmpty();
        verifyNoInteractions(discord);
        assertThat(store.scan.readComplete()).isFalse();
    }

    @Test
    @DisplayName("외부 수락 뒤 DB 기록 실패는 완료로 표시하지 않고 재시도한다")
    @SuppressWarnings("unchecked")
    void poll_keepsPendingWhenDeliveryCommitFails() throws Exception {
        doReturn(response(200, issue("1"), Map.of())).when(http).send(any(), any(HttpResponse.BodyHandler.class));
        when(discord.publishAndConfirm(any())).thenReturn(DeliveryResult.delivered());
        store.failDelivered = true;
        var first = worker();
        first.poll();
        assertThat(first.state()).isEqualTo(SentryWebIssuePoller.State.STORAGE_FAILED);
        assertThat(store.hasPending()).isTrue();
        assertThat(store.scan.readComplete()).isTrue();
        worker().poll();
        verify(discord, times(2)).publishAndConfirm(any());
        assertThat(store.hasPending()).isFalse();
    }

    @Test
    @DisplayName("5xx 조회 실패는 지수 backoff를 적용하고 빈 성공 뒤 오류 상태를 지운다")
    @SuppressWarnings("unchecked")
    void poll_backsOffServerFailures() throws Exception {
        doReturn(response(500, "SECRET", Map.of()), response(500, "SECRET", Map.of()), response(200, "[]", Map.of()))
                .when(http)
                .send(any(), any(HttpResponse.BodyHandler.class));
        Clock moving = new Clock() {
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                return now;
            }
        };
        var poller = new SentryWebIssuePoller(true, "test-token", http, new ObjectMapper(), discord, store, moving);
        poller.poll();
        now = now.plusSeconds(60);
        poller.poll();
        now = now.plusSeconds(60);
        poller.poll();
        verify(http, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
        now = now.plusSeconds(60);
        poller.poll();
        verify(http, times(3)).send(any(), any(HttpResponse.BodyHandler.class));
        assertThat(poller.state()).isEqualTo(SentryWebIssuePoller.State.NO_RECENT_ERROR);
        verifyNoInteractions(discord);
    }

    /** 프로세스 객체와 독립인 원장으로 재시작 계약을 검사한다. Mongo 원자성 검증은 별도다. */
    static class MemoryStore implements SentryPollStore {
        Scan scan = new Scan(Instant.parse("2026-09-11T23:55:00Z"), null, null, false);
        Instant readNotBefore = Instant.EPOCH;
        Map<String, Alert> rows = new LinkedHashMap<>();
        Map<String, Instant> retryAt = new HashMap<>();
        Set<String> done = new HashSet<>();
        boolean failPageSave;
        boolean failDelivered;

        public Lease acquire(Instant now) {
            return new Lease("owner", scan, readNotBefore);
        }

        public void saveScan(Lease lease, Scan value) {
            if (failPageSave && value.readComplete()) {
                failPageSave = false;
                throw new IllegalStateException("DB_FAILURE");
            }
            scan = value;
        }

        public void deferRead(Lease lease, Instant until) {
            readNotBefore = until;
        }

        public void enqueue(String key, Instant at) {
            rows.putIfAbsent(key, new Alert(key, at, 0));
        }

        public List<Alert> pending(Instant now) {
            return rows.values().stream()
                    .filter(a -> !done.contains(a.id())
                            && !retryAt.getOrDefault(a.id(), Instant.EPOCH).isAfter(now))
                    .toList();
        }

        public void delivered(String key) {
            if (failDelivered) {
                failDelivered = false;
                throw new IllegalStateException("DB_FAILURE");
            }
            done.add(key);
        }

        public void retry(String key, Instant at) {
            retryAt.put(key, at);
        }

        public boolean hasPending() {
            return rows.keySet().stream().anyMatch(key -> !done.contains(key));
        }

        public void release(Lease lease) {}
    }
}
