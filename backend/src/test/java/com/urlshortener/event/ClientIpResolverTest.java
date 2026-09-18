package com.urlshortener.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final String PROXY = "10.1.1.1";
    private static final String REAL_CLIENT = "203.0.113.9";

    private static MockHttpServletRequest requestFrom(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    @DisplayName("SECURITY: ignores X-Forwarded-For from an untrusted peer")
    void ignoresForgedHeaderFromUntrustedPeer() {
        // The header is caller-controlled. Believing it unconditionally would let any
        // client claim any identity - corrupting analytics and, once M5 lands, allowing
        // an attacker to present a fresh address on every request to evade rate limits.
        var resolver = new ClientIpResolver("");

        String resolved = resolver.resolve(requestFrom(REAL_CLIENT, "1.2.3.4"));

        assertThat(resolved).isEqualTo(REAL_CLIENT);
    }

    @Test
    @DisplayName("trusts nobody by default")
    void emptyConfigurationTrustsNobody() {
        var resolver = new ClientIpResolver("");

        assertThat(resolver.resolve(requestFrom(PROXY, "1.2.3.4"))).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("honours X-Forwarded-For when the peer is a configured proxy")
    void honoursHeaderFromTrustedProxy() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolve(requestFrom(PROXY, REAL_CLIENT))).isEqualTo(REAL_CLIENT);
    }

    @Test
    @DisplayName("takes the left-most entry of a proxy chain as the originating client")
    void usesLeftMostEntryOfChain() {
        var resolver = new ClientIpResolver(PROXY);
        var request = requestFrom(PROXY, REAL_CLIENT + ", 70.41.3.18, 150.172.238.178");

        assertThat(resolver.resolve(request)).isEqualTo(REAL_CLIENT);
    }

    @Test
    @DisplayName("parses a comma-separated trusted-proxy list, tolerating spaces")
    void parsesTrustedProxyList() {
        var resolver = new ClientIpResolver("10.0.0.1, " + PROXY + " ,10.0.0.3");

        assertThat(resolver.resolve(requestFrom(PROXY, REAL_CLIENT))).isEqualTo(REAL_CLIENT);
    }

    @Test
    @DisplayName("falls back to the peer when a trusted proxy sends no header")
    void trustedProxyWithoutHeaderFallsBackToPeer() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolve(requestFrom(PROXY, null))).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("falls back to the peer when a trusted proxy sends a blank header")
    void trustedProxyWithBlankHeaderFallsBackToPeer() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolve(requestFrom(PROXY, "   "))).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("a proxy in the list does not make every peer trusted")
    void trustIsPerPeerNotGlobal() {
        // Guards against a whitelist that is consulted but effectively ignored: a
        // request arriving directly must not benefit from another address being trusted.
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolve(requestFrom("198.51.100.7", "1.2.3.4")))
                .isEqualTo("198.51.100.7");
    }
}
