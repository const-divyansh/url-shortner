package com.urlshortener.validation.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.urlshortener.exception.ValidationException;
import com.urlshortener.validation.HostResolver;
import com.urlshortener.validation.ValidationTarget;

class TargetUrlHostSafetyRuleTest {

    /**
     * Stub resolver: tests state exactly where a host points, so no DNS is performed
     * and scenarios impossible to arrange with real records become testable.
     */
    private static HostResolver resolvesTo(String... addresses) {
        return host -> {
            InetAddress[] result = new InetAddress[addresses.length];
            for (int i = 0; i < addresses.length; i++) {
                result[i] = InetAddress.getByName(addresses[i]);
            }
            return result;
        };
    }

    private static HostResolver unresolvable() {
        return host -> {
            throw new UnknownHostException(host);
        };
    }

    private static ValidationTarget url(String value) {
        return new ValidationTarget(value, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/admin",
            "http://127.1.2.3/",
            "http://10.0.0.5/internal",
            "http://172.16.0.1/",
            "http://172.31.255.254/",
            "http://192.168.1.1/router",
            "http://169.254.169.254/latest/meta-data/",
            "http://0.0.0.0/",
            "http://100.64.0.1/",
            "http://[::1]/",
            "http://[fc00::1]/"
    })
    @DisplayName("blocks literal addresses that are not publicly routable")
    void blocksInternalLiteralAddresses(String value) {
        // Literal IPs resolve to themselves, so the stub mirrors real behaviour here.
        var rule = new TargetUrlHostSafetyRule(host -> InetAddress.getAllByName(host));

        assertThatThrownBy(() -> rule.validate(url(value)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.host_not_allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "ip6-localhost"})
    @DisplayName("blocks loopback hostnames regardless of case, without needing DNS")
    void blocksLoopbackHostnames(String host) {
        var rule = new TargetUrlHostSafetyRule(unresolvable());

        assertThatThrownBy(() -> rule.validate(url("http://" + host + "/")))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.host_not_allowed");
    }

    @Test
    @DisplayName("blocks a public-looking domain whose DNS points somewhere internal")
    void blocksPublicDomainResolvingToPrivateAddress() {
        // The case a text-only check cannot catch: nothing in the URL looks suspicious.
        var rule = new TargetUrlHostSafetyRule(resolvesTo("10.0.0.5"));

        assertThatThrownBy(() -> rule.validate(url("https://totally-fine.com/page")))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.host_not_allowed");
    }

    @Test
    @DisplayName("blocks a host resolving to both public and private addresses")
    void blocksMixedResolution() {
        // Checking only the first address would let this through.
        var rule = new TargetUrlHostSafetyRule(resolvesTo("93.184.216.34", "10.0.0.5"));

        assertThatThrownBy(() -> rule.validate(url("https://mixed.example/")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("fails closed when a host cannot be resolved")
    void rejectsUnresolvableHost() {
        var rule = new TargetUrlHostSafetyRule(unresolvable());

        assertThatThrownBy(() -> rule.validate(url("https://no-such-domain.invalid/")))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.host_unresolvable");
    }

    @Test
    @DisplayName("allows an ordinary public host")
    void allowsPublicHost() {
        var rule = new TargetUrlHostSafetyRule(resolvesTo("93.184.216.34"));

        assertThatCode(() -> rule.validate(url("https://example.com/some/path")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not disclose why a host was blocked")
    void blockReasonIsNotDisclosed() {
        // A prober should not be able to distinguish loopback from private range from
        // metadata endpoint - that would map our network for them.
        var rule = new TargetUrlHostSafetyRule(resolvesTo("169.254.169.254"));

        assertThatThrownBy(() -> rule.validate(url("https://probe.example/")))
                .hasMessageNotContainingAny("169.254", "metadata", "loopback", "private");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https:///path", "javascript:alert(1)"})
    @DisplayName("reports a missing host for inputs that parse but carry no authority")
    void rejectsMissingHost(String value) {
        // "https://" and "http://" are not listed here: Java's URI rejects them at
        // parse time, so they surface as url.malformed from the syntax rule instead.
        var rule = new TargetUrlHostSafetyRule(resolvesTo("93.184.216.34"));

        assertThatThrownBy(() -> rule.validate(url(value)))
                .isInstanceOf(ValidationException.class)
                .extracting("code").isEqualTo("url.no_host");
    }

    @Test
    @DisplayName("resolves the host without its IPv6 literal brackets")
    void stripsIpv6Brackets() {
        var captured = new String[1];
        HostResolver capturing = host -> {
            captured[0] = host;
            return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
        };

        new TargetUrlHostSafetyRule(capturing).validate(url("http://[2606:2800:220:1::1]/"));

        assertThat(captured[0]).doesNotContain("[").doesNotContain("]");
    }
}
