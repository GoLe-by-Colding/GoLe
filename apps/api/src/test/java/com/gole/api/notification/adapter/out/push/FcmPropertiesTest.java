package com.gole.api.notification.adapter.out.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FCM 설정 검증. 자격증명 누락이 조용히 통과하면 안 된다. */
class FcmPropertiesTest {

    @Test
    @DisplayName("프로젝트 ID 없이 켜면 기동을 거부한다")
    void validate_rejectsMissingProjectId() {
        FcmProperties properties = new FcmProperties(true, "", "/tmp/key.json", "");

        assertThatThrownBy(properties::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FCM_PROJECT_ID");
    }

    @Test
    @DisplayName("자격증명이 둘 다 없으면 기동을 거부한다")
    void validate_rejectsMissingCredentials() {
        FcmProperties properties = new FcmProperties(true, "gole-prod", "", "");

        assertThatThrownBy(properties::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FCM_CREDENTIALS_PATH");
    }

    @Test
    @DisplayName("파일 경로가 base64보다 우선한다")
    void openCredentials_prefersFileOverInline(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("key.json");
        Files.writeString(keyFile, "{\"from\":\"file\"}");
        String inline = Base64.getEncoder().encodeToString("{\"from\":\"inline\"}".getBytes(StandardCharsets.UTF_8));
        FcmProperties properties = new FcmProperties(true, "gole-prod", keyFile.toString(), inline);

        try (InputStream in = properties.openCredentials()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("file");
        }
    }

    @Test
    @DisplayName("파일이 없으면 base64를 쓴다")
    void openCredentials_fallsBackToInline() throws IOException {
        String inline = Base64.getEncoder().encodeToString("{\"from\":\"inline\"}".getBytes(StandardCharsets.UTF_8));
        FcmProperties properties = new FcmProperties(true, "gole-prod", "", inline);

        try (InputStream in = properties.openCredentials()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("inline");
        }
    }

    @Test
    @DisplayName("읽을 수 없는 파일 경로는 조용히 인라인으로 넘어가지 않는다")
    void openCredentials_failsLoudlyOnUnreadablePath() {
        FcmProperties properties = new FcmProperties(true, "gole-prod", "/nonexistent/key.json", "");

        // 조용히 넘어가면 "왜 푸시가 안 가지"를 추적할 수 없다.
        assertThatThrownBy(properties::openCredentials).isInstanceOf(IOException.class);
    }
}
