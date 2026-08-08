package com.gole.api.order.adapter.in.web;

import com.gole.api.common.exception.BadRequestException;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import kotlinx.serialization.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** PortOne Standard Webhooks 서명과 타임스탬프를 원문 본문 기준으로 검증한다. */
@Component
public class PortOneWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(PortOneWebhookVerifier.class);

    private final WebhookVerifier verifier;

    public PortOneWebhookVerifier(@Value("${portone.webhook-secret:}") String webhookSecret) {
        this.verifier = webhookSecret.isBlank() ? null : createVerifier(webhookSecret);
    }

    /**
     * 로컬 스텁 환경에서는 검증기를 비활성화할 수 있다. 실결제를 켜면
     * {@link com.gole.api.order.adapter.out.payment.PaymentConfigurationGuard}가 secret 누락을 차단한다.
     */
    public void verify(String body, String messageId, String signature, String timestamp) {
        if (verifier == null) {
            return;
        }
        try {
            verifier.verify(body, messageId, signature, timestamp);
        } catch (WebhookVerificationException | SerializationException ex) {
            log.warn("[PortOne webhook] signature verification failed: {}", ex.getMessage());
            throw new BadRequestException("INVALID_PAYMENT_WEBHOOK", "유효하지 않은 결제 웹훅입니다.");
        }
    }

    private static WebhookVerifier createVerifier(String webhookSecret) {
        try {
            return new WebhookVerifier(webhookSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PORTONE_WEBHOOK_SECRET must be a valid PortOne webhook secret", ex);
        }
    }
}
