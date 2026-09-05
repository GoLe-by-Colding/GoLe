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
        this.verifier = webhookSecret == null || webhookSecret.isBlank() ? null : createVerifier(webhookSecret);
    }

    /** 검증기나 서명 비밀이 없으면 어떤 환경에서도 웹훅을 신뢰하지 않는다. */
    public void verify(String body, String messageId, String signature, String timestamp) {
        if (verifier == null) {
            throw new BadRequestException("INVALID_PAYMENT_WEBHOOK", "유효하지 않은 결제 웹훅입니다.");
        }
        try {
            verifier.verify(body, messageId, signature, timestamp);
        } catch (WebhookVerificationException | SerializationException ex) {
            log.warn(
                    "[PortOne webhook] signature verification failed cause={}",
                    ex.getClass().getSimpleName());
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
