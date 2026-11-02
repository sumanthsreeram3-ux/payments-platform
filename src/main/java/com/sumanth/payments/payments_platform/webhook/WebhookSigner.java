package com.sumanth.payments.payments_platform.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSigner() {}

    /** Returns the header value: v1=<hex HMAC-SHA256 of "<timestamp>.<body>">. */
    public static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What a receiver should do: check freshness, then compare signatures in constant time. */
    public static boolean verify(String secret, long timestamp, String body, String header,
                                 long toleranceSeconds, long nowEpochSeconds) {
        if (header == null || Math.abs(nowEpochSeconds - timestamp) > toleranceSeconds) {
            return false;
        }
        byte[] expected = sign(secret, timestamp, body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8));
    }
}