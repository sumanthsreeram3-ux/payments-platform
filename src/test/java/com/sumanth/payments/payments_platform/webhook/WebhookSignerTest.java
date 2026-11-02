package com.sumanth.payments.payments_platform.webhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignerTest {

    private static final String BODY = "{\"paymentId\":\"p1\"}";

    @Test
    void validSignatureVerifies() {
        String signature = WebhookSigner.sign("whsec_test", 1_000L, BODY);
        assertTrue(signature.startsWith("v1="));
        assertTrue(WebhookSigner.verify("whsec_test", 1_000L, BODY, signature, 300, 1_100L));
    }

    @Test
    void tamperedBodyOrWrongSecretIsRejected() {
        String signature = WebhookSigner.sign("whsec_test", 1_000L, BODY);
        assertFalse(WebhookSigner.verify("whsec_test", 1_000L, "{\"paymentId\":\"p2\"}", signature, 300, 1_100L));
        assertFalse(WebhookSigner.verify("whsec_other", 1_000L, BODY, signature, 300, 1_100L));
    }

    @Test
    void staleTimestampIsRejectedEvenWithAValidSignature() {
        String signature = WebhookSigner.sign("whsec_test", 1_000L, BODY);
        assertFalse(WebhookSigner.verify("whsec_test", 1_000L, BODY, signature, 300, 2_000L));
    }
}