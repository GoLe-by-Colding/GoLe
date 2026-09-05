package com.gole.api.notification.adapter.out.push;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM 발송 설정. 서비스 계정 키를 <b>파일 경로 또는 base64 인라인</b> 둘 중 하나로 받는다.
 *
 * <p>파일이 우선이고 더 안전하다 — 이 JSON 하나면 앱 사용자 전원에게 푸시를 보낼 수 있는데,
 * 환경변수는 프로세스 목록·크래시 덤프·로그로 새기 쉬운 반면 파일은 권한으로 막을 수 있다.
 *
 * <p>그런데도 인라인을 남기는 이유는 배포 현실이다. 운영 키 볼트가 <b>환경변수만</b> 다루므로
 * 파일을 서버에 따로 올릴 수단이 없는 환경이 있다. 그 경우 인라인이 유일한 경로다.
 * base64로 받는 것은 JSON의 줄바꿈이 env 파일 형식을 깨기 때문이다.
 */
@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(boolean enabled, String projectId, String credentialsPath, String credentialsBase64) {

    public FcmProperties {
        projectId = nullToEmpty(projectId);
        credentialsPath = nullToEmpty(credentialsPath);
        credentialsBase64 = nullToEmpty(credentialsBase64);
    }

    void validateEnabledConfiguration() {
        if (projectId.isBlank()) {
            throw new IllegalStateException("FCM_ENABLED=true requires FCM_PROJECT_ID");
        }
        if (credentialsPath.isBlank() && credentialsBase64.isBlank()) {
            throw new IllegalStateException("FCM_ENABLED=true requires FCM_CREDENTIALS_PATH or FCM_CREDENTIALS_BASE64");
        }
    }

    /** 호출자가 닫는다. 파일이 우선 — 둘 다 있으면 파일을 쓴다. */
    InputStream openCredentials() throws IOException {
        if (!credentialsPath.isBlank()) {
            Path path = Path.of(credentialsPath);
            if (!Files.isReadable(path)) {
                throw new IOException("FCM 서비스 계정 키를 읽을 수 없습니다: " + credentialsPath);
            }
            return Files.newInputStream(path);
        }
        try {
            return new ByteArrayInputStream(Base64.getDecoder().decode(credentialsBase64));
        } catch (IllegalArgumentException notBase64) {
            throw new IOException("FCM_CREDENTIALS_BASE64가 유효한 base64가 아닙니다", notBase64);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
