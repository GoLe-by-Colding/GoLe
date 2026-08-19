package com.gole.api.shipping.adapter.out.tracker;

import com.gole.api.shipping.application.port.out.DeliveryTrackerPort;
import com.gole.api.shipping.domain.model.DeliveryStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Delivery Tracker(tracker.delivery) 실 어댑터. (R6, F2/F3)
 *
 * <p>{@code shipping.tracker.enabled=true} + 클라이언트 자격증명이 있을 때만 활성화된다.
 * GraphQL 단일 엔드포인트에 {@code track(carrierId, trackingNumber)}를 질의하고,
 * 표준 상태 코드를 도메인 {@link DeliveryStatus}로 정규화한다. 원문 상태명은 그대로 보존한다(R2.2).
 *
 * <p>모든 실패(네트워크·인증·미지원 송장)는 {@code UNKNOWN}으로 접는다 — 조회 실패가
 * 주문 흐름을 막으면 안 된다(R2.3). 연속 UNKNOWN은 파이프라인이 예외 큐로 올린다.
 */
@Component
@ConditionalOnProperty(name = "shipping.tracker.enabled", havingValue = "true")
public class DeliveryTrackerApiAdapter implements DeliveryTrackerPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackerApiAdapter.class);

    /**
     * Delivery Tracker 표준 상태 코드 → 도메인 상태. (F3 매핑 테이블)
     * 목록에 없는 코드는 UNKNOWN — 새 코드가 추가되어도 오동작 대신 예외 큐로 흘러간다.
     */
    private static final Map<String, DeliveryStatus> STATUS_MAP = Map.of(
            "INFORMATION_RECEIVED", DeliveryStatus.PENDING,
            "AT_PICKUP", DeliveryStatus.IN_TRANSIT,
            "IN_TRANSIT", DeliveryStatus.IN_TRANSIT,
            "OUT_FOR_DELIVERY", DeliveryStatus.IN_TRANSIT,
            "ATTEMPT_FAIL", DeliveryStatus.IN_TRANSIT,
            "AVAILABLE_FOR_PICKUP", DeliveryStatus.IN_TRANSIT,
            "DELIVERED", DeliveryStatus.DELIVERED);

    private static final String TRACK_QUERY =
            """
            query Track($carrierId: ID!, $trackingNumber: String!) {
              track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
                lastEvent { status { code name } }
              }
            }""";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String clientId;
    private final String clientSecret;
    private final Duration timeout;

    public DeliveryTrackerApiAdapter(
            ObjectMapper objectMapper,
            @Value("${shipping.tracker.api-base:https://apis.tracker.delivery/graphql}") String apiBase,
            @Value("${shipping.tracker.client-id:}") String clientId,
            @Value("${shipping.tracker.client-secret:}") String clientSecret,
            @Value("${shipping.tracker.timeout:PT5S}") Duration timeout) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(apiBase);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.timeout = timeout;
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public TrackingResult track(TrackingQuery query) {
        if (!isConfigured()) {
            log.warn("Delivery Tracker 자격증명이 없어 조회를 건너뜁니다 (shipping.tracker.client-id/secret)");
            return new TrackingResult(DeliveryStatus.UNKNOWN, null);
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "query",
                    TRACK_QUERY,
                    "variables",
                    Map.of(
                            "carrierId", query.carrier().trackerId(),
                            "trackingNumber", query.waybill().value())));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "TRACKQL-API-KEY " + clientId + ":" + clientSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Delivery Tracker 응답 오류 status={}", response.statusCode());
                return new TrackingResult(DeliveryStatus.UNKNOWN, null);
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TrackingResult(DeliveryStatus.UNKNOWN, null);
        } catch (Exception e) {
            log.warn("Delivery Tracker 조회 실패 carrier={} : {}", query.carrier().trackerId(), e.getMessage());
            return new TrackingResult(DeliveryStatus.UNKNOWN, null);
        }
    }

    private TrackingResult parse(String body) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode lastEvent = root.path("data").path("track").path("lastEvent");
        if (lastEvent.isMissingNode() || lastEvent.isNull()) {
            // 등록 직후에는 트래커에 이벤트가 없을 수 있다 — 미접수로 본다.
            return new TrackingResult(DeliveryStatus.PENDING, null);
        }
        String code = lastEvent.path("status").path("code").asString("");
        String name = lastEvent.path("status").path("name").asString(null);
        DeliveryStatus status = STATUS_MAP.getOrDefault(code, DeliveryStatus.UNKNOWN);
        return new TrackingResult(status, name == null || name.isBlank() ? code : name);
    }
}
