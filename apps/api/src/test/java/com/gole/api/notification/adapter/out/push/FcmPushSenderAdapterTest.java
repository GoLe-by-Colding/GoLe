package com.gole.api.notification.adapter.out.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gole.api.notification.application.port.out.PushSenderPort.PushMessage;
import com.gole.api.notification.domain.model.DevicePlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmPushSenderAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("알림 제목·본문과 탭 경로를 data 로 싣고 기본 알림음을 켠다")
    void body_carriesNotificationLinkAndDefaultSound() throws Exception {
        JsonNode message = mapper.readTree(FcmPushSenderAdapter.body(
                        new PushMessage("token-1", DevicePlatform.IOS, "GoLe", "관심 세트 「에펠탑」 곧 단종돼요", "/sets/10307")))
                .get("message");

        assertThat(message.get("token").asText()).isEqualTo("token-1");
        assertThat(message.at("/notification/title").asText()).isEqualTo("GoLe");
        assertThat(message.at("/notification/body").asText()).isEqualTo("관심 세트 「에펠탑」 곧 단종돼요");
        assertThat(message.at("/data/link").asText()).isEqualTo("/sets/10307");
        assertThat(message.at("/apns/payload/aps/sound").asText()).isEqualTo("default");
        assertThat(message.at("/android/notification/default_sound").asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("탭 경로가 없으면 data 를 싣지 않는다")
    void body_omitsDataWithoutLink() throws Exception {
        JsonNode message = mapper.readTree(FcmPushSenderAdapter.body(
                        new PushMessage("token-1", DevicePlatform.ANDROID, "GoLe", "본문", " ")))
                .get("message");

        assertThat(message.has("data")).isFalse();
        assertThat(message.at("/apns/payload/aps/sound").asText()).isEqualTo("default");
    }
}
