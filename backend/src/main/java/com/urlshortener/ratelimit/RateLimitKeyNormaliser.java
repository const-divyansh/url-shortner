package com.urlshortener.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reduces a client address to the identity a request budget should be counted against.
 *
 * <p>Deliberately separate from the address {@code ClientIpResolver} produces, and
 * applied only to rate limiting. Analytics hashes the full address on purpose, because
 * distinguishing two visitors behind one prefix is the point there. Counting budgets
 * has the opposite requirement, so normalising inside the resolver would silently
 * coarsen click data as a side effect of a rate limiting change.
 *
 * <p><strong>Two problems this solves, both of which let a caller exceed their
 * budget.</strong>
 *
 * <ul>
 *   <li><em>The same host counted twice.</em> A dual-stack client reaches the service
 *       over IPv4 or IPv6 at will. Counted as literal strings those are two identities,
 *       so simply alternating doubles the allowance. Loopback is the visible case -
 *       {@code 127.0.0.1} and {@code ::1} are one machine - but the same is true of any
 *       dual-stack client.</li>
 *   <li><em>IPv6 address rotation.</em> A subscriber is typically delegated an entire
 *       {@code /64}, which is 18 quintillion addresses. Counting per address means a
 *       limit can be evaded indefinitely without any special effort, because using a
 *       fresh source address is free. Counting per {@code /64} makes the budget apply
 *       to the subscriber rather than to an address they can discard.</li>
 * </ul>
 *
 * <p>IPv4 is left at full address: its prefixes are not delegated in a comparable way,
 * and truncating would put unrelated customers of one provider into a single bucket.
 */
@Component
public class RateLimitKeyNormaliser {

    private static final Logger log = LoggerFactory.getLogger(RateLimitKeyNormaliser.class);

    /**
     * A single identity for any loopback address, so {@code 127.0.0.1} and {@code ::1}
     * are one caller. They are the same machine, and treating them as two is what makes
     * a locally configured limit appear not to work.
     */
    private static final String LOOPBACK = "loopback";

    /** Bytes of an IPv6 address that identify the delegated prefix - the first /64. */
    private static final int IPV6_PREFIX_BYTES = 8;

    /**
     * Only literal addresses are parsed. Anything else is passed through untouched
     * rather than handed to {@link InetAddress}, whose name resolution would otherwise
     * turn a malformed or hostile value into a DNS lookup on the request path.
     */
    private static final String IP_LITERAL_CHARS = "[0-9a-fA-F:.]+";

    public String normalise(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }

        // A link-local address carries a zone ("fe80::1%eth0") that identifies the
        // local interface, not the caller, and would otherwise split one caller across
        // buckets.
        String candidate = address.trim();
        int zone = candidate.indexOf('%');
        if (zone >= 0) {
            candidate = candidate.substring(0, zone);
        }

        if (!candidate.matches(IP_LITERAL_CHARS)) {
            // Not something we can interpret. Returned as-is so the caller is still
            // counted - refusing to count an address we cannot parse would hand an
            // attacker an exemption for the cost of a malformed header.
            log.debug("Not an IP literal, counting verbatim: {}", candidate);
            return candidate;
        }

        InetAddress parsed;
        try {
            parsed = InetAddress.getByName(candidate);
        } catch (UnknownHostException e) {
            log.debug("Unparseable address, counting verbatim: {}", candidate);
            return candidate;
        }

        if (parsed.isLoopbackAddress()) {
            return LOOPBACK;
        }

        if (parsed instanceof Inet6Address) {
            // An IPv4-mapped address ("::ffff:203.0.113.7") arrives as Inet4Address
            // from getByName, so reaching here means a genuine IPv6 address.
            byte[] prefix = Arrays.copyOf(parsed.getAddress(), IPV6_PREFIX_BYTES);
            return "v6/64:" + toHex(prefix);
        }

        return parsed.getHostAddress();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
