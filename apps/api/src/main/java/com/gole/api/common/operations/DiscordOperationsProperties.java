package com.gole.api.common.operations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Discord 운영 알림 설정. webhook URL은 반드시 배포 환경변수로 주입한다. */
@Component
@ConfigurationProperties(prefix = "gole.discord")
public class DiscordOperationsProperties {

    private boolean enabled;
    private String environment = "local";
    private String webhookUrl = "";
    private String accountWebhookUrl = "";
    private String paymentWebhookUrl = "";
    private String operationsWebhookUrl = "";
    private String avatarUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getAccountWebhookUrl() {
        return accountWebhookUrl;
    }

    public void setAccountWebhookUrl(String accountWebhookUrl) {
        this.accountWebhookUrl = accountWebhookUrl;
    }

    public String getPaymentWebhookUrl() {
        return paymentWebhookUrl;
    }

    public void setPaymentWebhookUrl(String paymentWebhookUrl) {
        this.paymentWebhookUrl = paymentWebhookUrl;
    }

    public String getOperationsWebhookUrl() {
        return operationsWebhookUrl;
    }

    public void setOperationsWebhookUrl(String operationsWebhookUrl) {
        this.operationsWebhookUrl = operationsWebhookUrl;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String webhookFor(OperationalEvent.Category category) {
        String categoryUrl =
                switch (category) {
                    case ACCOUNT -> accountWebhookUrl;
                    case PAYMENT -> paymentWebhookUrl;
                    case ADMIN -> operationsWebhookUrl;
                    case APPLICATION -> operationsWebhookUrl;
                };
        return hasText(categoryUrl) ? categoryUrl : webhookUrl;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
