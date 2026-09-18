package com.urlshortener.validation.rule;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.HostResolver;
import com.urlshortener.validation.ValidationRule;
import com.urlshortener.validation.ValidationTarget;

/**
 * Blocks targets that point at internal infrastructure (SSRF defence).
 *
 * <p>Checking the literal host text is not enough: any attacker can register a public
 * domain whose DNS record points at a private address, which would sail past a
 * text-only check. So the host is resolved and <em>every</em> returned address is
 * examined - a host resolving to both a public and a private address is still refused.
 *
 * <p>Runs last in the chain because it is the only rule that performs I/O.
 *
 * <p><strong>Known limitation (time-of-check/time-of-use).</strong> Resolution happens
 * when the link is created. A domain that resolves publicly today can be repointed at
 * an internal address tomorrow, and the stored link would still redirect there.
 * Closing that requires re-resolving on every redirect; recorded as a gap rather than
 * implied to be covered.
 */
@Component
@Order(RuleOrder.HOST_SAFETY)
public class TargetUrlHostSafetyRule implements ValidationRule {

    /**
     * Hostnames that denote the local machine without needing DNS.
     */
    private static final Set<String> BLOCKED_HOST_NAMES = Set.of(
            "localhost", "localhost.localdomain", "ip6-localhost", "ip6-loopback");

    private final HostResolver hostResolver;

    public TargetUrlHostSafetyRule(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    @Override
    public void validate(ValidationTarget target) {
        URI uri = TargetUrlSyntaxRule.parse(target.targetUrl());
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            // Checked here rather than in the syntax rule so that inputs carrying a
            // scheme but no authority - "javascript:alert(1)", "https:///path" - are
            // first judged on their scheme, which is the more accurate diagnosis.
            throw new ValidationException("url.no_host",
                    "Enter a complete URL with a website name, for example https://example.com.");
        }
        String host = stripBrackets(uri.getHost()).toLowerCase(Locale.ROOT);

        if (BLOCKED_HOST_NAMES.contains(host)) {
            throw blocked();
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            // Fail closed: if we cannot determine where a host points, we cannot
            // establish that it is safe. Rejecting an unresolvable host is preferable
            // to storing a link we were unable to check.
            throw new ValidationException("url.host_unresolvable",
                    "That website could not be reached. Check the address and try again.");
        }

        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw blocked();
            }
        }
    }

    /**
     * True for any address that is not a normal, routable public destination.
     */
    private static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress()      // 0.0.0.0, ::
                || address.isLoopbackAddress()   // 127.0.0.0/8, ::1
                || address.isLinkLocalAddress()  // 169.254.0.0/16 - includes cloud metadata
                || address.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address ipv4) {
            int first = ipv4.getAddress()[0] & 0xFF;
            int second = ipv4.getAddress()[1] & 0xFF;
            // 100.64.0.0/10, carrier-grade NAT - not covered by isSiteLocalAddress.
            return first == 100 && second >= 64 && second <= 127;
        }
        if (address instanceof Inet6Address ipv6) {
            int first = ipv6.getAddress()[0] & 0xFF;
            // fc00::/7, IPv6 unique local - the IPv6 analogue of a private range, and
            // not reported by isSiteLocalAddress.
            return (first & 0xFE) == 0xFC;
        }
        return false;
    }

    /**
     * IPv6 literals appear in URLs as "[::1]"; strip the brackets before resolving.
     */
    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    /**
     * One message for every blocked case, deliberately.
     *
     * <p>Distinguishing "loopback" from "private range" from "metadata endpoint" would
     * tell a prober what our network looks like.
     */
    private static ValidationException blocked() {
        return new ValidationException("url.host_not_allowed",
                "That URL points to an internal or unsupported host.");
    }
}
