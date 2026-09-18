package com.urlshortener.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Generates session tokens and derives their lookup hash.
 *
 * <p>The stored value is a one-way SHA-256 digest of a high-entropy, server-generated
 * secret. Unlike passwords, these tokens are not user-chosen and are not low-entropy,
 * so a fast digest is acceptable for equality lookup without keeping the raw token.
 */
@Component
public class SessionTokenCodec {

    /** Sent as a bearer credential, e.g. {@code Authorization: Bearer <token>}. */
    public static final String AUTH_SCHEME = "Bearer";

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
