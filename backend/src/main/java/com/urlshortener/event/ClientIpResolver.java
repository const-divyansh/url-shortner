package com.urlshortener.event;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Determines the client address for a request.
 *
 * <p><strong>Why {@code X-Forwarded-For} is not trusted by default.</strong> It is an
 * ordinary request header, so any caller can set it to anything. Trusting it
 * unconditionally would let a client forge its own address - polluting analytics, and
 * later defeating the per-IP rate limiting planned for M5, since an attacker could
 * present a different address on every request.
 *
 * <p>It is therefore honoured only when the immediate peer is a configured trusted
 * proxy. Behind such a proxy the header is meaningful, because the proxy appends the
 * real peer itself. With no trusted proxies configured - the default - the socket
 * address is always used.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${app.analytics.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = trustedProxies == null || trustedProxies.isBlank()
                ? Set.of()
                : Set.copyOf(List.of(trustedProxies.split("\\s*,\\s*")));
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();

        if (!trustedProxies.contains(peer)) {
            return peer;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        // The header is a chain: "client, proxy1, proxy2". The left-most entry is the
        // originating client.
        int comma = forwarded.indexOf(',');
        String client = (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();

        return client.isEmpty() ? peer : client;
    }
}
