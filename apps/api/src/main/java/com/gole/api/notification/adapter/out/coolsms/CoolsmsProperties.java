package com.gole.api.notification.adapter.out.coolsms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** CoolSMS 알림톡 연동 설정. 자격증명은 환경변수로만 주입한다. */
@ConfigurationProperties(prefix = "coolsms")
public record CoolsmsProperties(boolean enabled, String apiKey, String apiSecret, String pfId) {

    public CoolsmsProperties {
        apiKey = nullToEmpty(apiKey);
        apiSecret = nullToEmpty(apiSecret);
        pfId = nullToEmpty(pfId);
    }

    void validateEnabledConfiguration() {
        requireText(apiKey, "COOLSMS_API_KEY");
        requireText(apiSecret, "COOLSMS_API_SECRET");
        requireText(pfId, "COOLSMS_PF_ID");
    }

    private static void requireText(String value, String environmentVariable) {
        if (value.isBlank()) {
            throw new IllegalStateException("COOLSMS_ENABLED=true requires " + environmentVariable);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
