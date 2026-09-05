package com.gole.api.notification.adapter.out.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gole.api.notification.application.port.out.PushSenderPort;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FCM HTTP v1 발송 어댑터.
 *
 * <p>Firebase Admin SDK 전체 대신 인증 라이브러리 하나만 쓴다. 필요한 것은 액세스 토큰 발급·갱신
 * 뿐인데, Admin SDK는 Firestore·Auth·Storage까지 끌고 들어온다. 반대로 JWT 서명을 직접 짜면
 * 만료·시계 오차·갱신 경합을 손으로 관리하게 된다 — 그 부분만 검증된 라이브러리에 맡긴다.
 *
 * <p>iOS·Android 모두 이 경로로 나간다. APNs는 FCM이 대신 중계한다.
 */
public final class FcmPushSenderAdapter implements PushSenderPort {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSenderAdapter.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final GoogleCredentials credentials;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FcmPushSenderAdapter(FcmProperties properties) {
        this.credentials = loadCredentials(properties);
        this.endpoint =
                URI.create("https://fcm.googleapis.com/v1/projects/" + properties.projectId() + "/messages:send");
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    private static GoogleCredentials loadCredentials(FcmProperties properties) {
        try (InputStream in = properties.openCredentials()) {
            return GoogleCredentials.fromStream(in).createScoped(SCOPE);
        } catch (IOException e) {
            throw new IllegalStateException("FCM 서비스 계정 키를 읽을 수 없습니다", e);
        }
    }

    @Override
    public PushOutcome send(PushMessage message) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(endpoint)
                            .header("Authorization", "Bearer " + accessToken())
                            .header("Content-Type", "application/json")
                            .timeout(TIMEOUT)
                            .POST(HttpRequest.BodyPublishers.ofString(body(message)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return classify(response);
        } catch (IOException e) {
            log.warn("FCM 발송 실패(네트워크)", e);
            return PushOutcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushOutcome.FAILED;
        } catch (RuntimeException e) {
            log.warn("FCM 발송 실패", e);
            return PushOutcome.FAILED;
        }
    }

    private String accessToken() throws IOException {
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    /**
     * 404 UNREGISTERED와 400 INVALID_ARGUMENT는 <b>토큰이 죽었다</b>는 뜻이므로 재시도 대상이
     * 아니다. 나머지 실패는 일시적일 수 있으니 토큰을 살려 둔다.
     */
    private PushOutcome classify(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return PushOutcome.ACCEPTED;
        }
        if (status == 404 || status == 400) {
            log.debug("FCM이 토큰을 거부했다 status={} body={}", status, response.body());
            return PushOutcome.TOKEN_INVALID;
        }
        log.warn("FCM 발송 실패 status={} body={}", status, response.body());
        return PushOutcome.FAILED;
    }

    private String body(PushMessage message) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode fcmMessage = root.putObject("message");
        fcmMessage.put("token", message.token());

        ObjectNode notification = fcmMessage.putObject("notification");
        notification.put("title", message.title());
        notification.put("body", message.body());

        // 탭 처리에 필요한 경로. notification이 아니라 data로 실어야 앱이 포그라운드·백그라운드
        // 어느 상태에서 받아도 같은 값을 읽을 수 있다.
        if (message.link() != null && !message.link().isBlank()) {
            fcmMessage.putObject("data").put("link", message.link());
        }
        return root.toString();
    }
}
