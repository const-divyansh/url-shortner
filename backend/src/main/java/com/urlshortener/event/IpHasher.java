package com.urlshortener.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a client IP address into a stored identifier, so raw addresses are never
 * persisted (NFR8).
 *
 * <p><strong>Why a secret is mixed in.</strong> A plain {@code SHA-256(ip)} would be
 * trivially reversible: IPv4 has only about 4.3 billion addresses, which commodity
 * hardware can hash exhaustively in seconds. Anyone obtaining the database could build
 * a lookup table and recover every address. Hashing alone protects unpredictable
 * inputs; IP addresses are a small, fully enumerable set.
 *
 * <p><strong>This is a pepper, not a salt.</strong> A conventional per-record random
 * salt would make the same visitor hash differently on every visit, destroying the
 * repeat-visitor correlation analytics needs. Instead one secret value is used for all
 * records - which is why it must live in the environment and <em>never</em> in the
 * database: storing it alongside the hashes would hand an attacker both halves at once.
 *
 * <p><strong>Accepted limitation.</strong> Because the pepper is fixed, a leak makes
 * every historical hash reversible, not merely future ones. Rotating it would cap that
 * blast radius at one period, at the cost of repeat-visitor counts only working within
 * a window. Fixed was chosen deliberately at this scope.
 */
@Component
public class IpHasher {

    private final byte[] pepper;

    public IpHasher(@Value("${app.analytics.ip-pepper}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("app.analytics.ip-pepper must be configured");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param ip client address, or null when unavailable
     * @return 64-character hex digest, or null if {@code ip} was null or blank
     */
    public String hash(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            digest.update(ip.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
