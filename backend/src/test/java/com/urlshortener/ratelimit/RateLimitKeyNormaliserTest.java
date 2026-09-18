package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitKeyNormaliserTest {

    private final RateLimitKeyNormaliser normaliser = new RateLimitKeyNormaliser();

    @Test
    @DisplayName("REGRESSION: loopback is one caller regardless of IP family")
    void loopbackIsOneCaller() {
        // Found in manual testing: a locally configured limit appeared not to work
        // because the browser and curl reached the service over different stacks, so
        // one machine held two budgets. The same doubling applies to any dual-stack
        // client, not just localhost.
        assertThat(normaliser.normalise("127.0.0.1"))
                .isEqualTo(normaliser.normalise("::1"));
    }

    @Test
    @DisplayName("an IPv4-mapped address counts as the IPv4 address it wraps")
    void ipv4MappedUnwrapsToIpv4() {
        // "::ffff:203.0.113.7" and "203.0.113.7" are the same host reached through a
        // dual-stack socket; counting them apart would again double the budget.
        assertThat(normaliser.normalise("::ffff:203.0.113.7"))
                .isEqualTo(normaliser.normalise("203.0.113.7"));
    }

    @Test
    @DisplayName("REGRESSION: IPv6 addresses in one /64 share a budget")
    void ipv6AddressesInSamePrefixShareABudget() {
        // A subscriber is normally delegated an entire /64. Counting per address would
        // let one caller rotate through 18 quintillion of them, so the limit could be
        // evaded indefinitely at no cost.
        String first = normaliser.normalise("2001:db8:abcd:1234::1");
        String second = normaliser.normalise("2001:db8:abcd:1234:ffff:ffff:ffff:ffff");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("different IPv6 /64s are different callers")
    void differentIpv6PrefixesAreDifferentCallers() {
        // The counterpart to the test above: prefix grouping must not collapse
        // unrelated subscribers into one bucket, which would let one abuser throttle
        // everybody else.
        assertThat(normaliser.normalise("2001:db8:abcd:1234::1"))
                .isNotEqualTo(normaliser.normalise("2001:db8:abcd:9999::1"));
    }

    @Test
    @DisplayName("IPv4 addresses are not truncated")
    void ipv4IsNotTruncated() {
        // IPv4 prefixes are not delegated the way IPv6 /64s are, so truncating would
        // put unrelated customers of one provider into a single bucket.
        assertThat(normaliser.normalise("203.0.113.7"))
                .isNotEqualTo(normaliser.normalise("203.0.113.8"));
    }

    @Test
    @DisplayName("a link-local zone index is stripped")
    void zoneIndexIsStripped() {
        // The zone names a local interface, not the caller, so leaving it in would
        // split one caller across buckets.
        assertThat(normaliser.normalise("fe80::1%eth0"))
                .isEqualTo(normaliser.normalise("fe80::1%en0"));
    }

    @Test
    @DisplayName("an unparseable value is still counted, never exempted")
    void unparseableValueIsStillCounted() {
        // Refusing to count what we cannot parse would hand out an exemption for the
        // price of a malformed header.
        assertThat(normaliser.normalise("not-an-address")).isEqualTo("not-an-address");
    }

    @Test
    @DisplayName("a hostname is never resolved")
    void hostnamesAreNotResolved() {
        // InetAddress.getByName would perform a DNS lookup on the request path, which
        // an attacker could aim at a host of their choosing.
        assertThat(normaliser.normalise("evil.example.com")).isEqualTo("evil.example.com");
    }

    @Test
    @DisplayName("null and blank pass through untouched")
    void nullAndBlankPassThrough() {
        // The limiter treats an absent key as "allow and skip"; that decision belongs
        // there, so this must not invent a key.
        assertThat(normaliser.normalise(null)).isNull();
        assertThat(normaliser.normalise("   ")).isBlank();
    }
}
