package com.gole.api.order.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gole.api.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PortOneWebhookVerifierTest {

    private static final byte[] SECRET = "gole-portone-webhook-test-secret".getBytes(StandardCharsets.UTF_8);
    private static final String ENCODED_SECRET = "whsec_" + Base64.getEncoder().encodeToString(SECRET);
    private static final String BODY = "{\"type\":\"Transaction.Paid\",\"timestamp\":\"2026-08-09T00:00:00Z\","
            + "\"data\":{\"paymentId\":\"order-1\",\"storeId\":\"store-1\",\"transactionId\":\"tx-1\"}}";

    @Test
    void acceptsValidStandardWebhookSignature() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String messageId = "message-1";
        PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(ENCODED_SECRET);

        assertThatCode(() -> verifier.verify(
                        BODY, messageId, signature(messageId, timestamp, BODY), Long.toString(timestamp)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBodyTamperingAndStaleReplay() throws Exception {
        long now = Instant.now().getEpochSecond();
        String messageId = "message-2";
        PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(ENCODED_SECRET);

        assertThatThrownBy(() ->
                        verifier.verify(BODY + " ", messageId, signature(messageId, now, BODY), Long.toString(now)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("유효하지 않은");

        long staleTimestamp = now - 301;
        assertThatThrownBy(() -> verifier.verify(
                        BODY, messageId, signature(messageId, staleTimestamp, BODY), Long.toString(staleTimestamp)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("유효하지 않은");
    }

    @Test
    void missingSecretAlwaysFailsClosed() {
        PortOneWebhookVerifier verifier = new PortOneWebhookVerifier("");

        assertThatThrownBy(() -> verifier.verify(BODY, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("유효하지 않은");
    }

    private static String signature(String messageId, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        byte[] signature = mac.doFinal((messageId + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signature);
    }
}
