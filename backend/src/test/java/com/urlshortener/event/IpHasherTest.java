package com.urlshortener.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IpHasherTest {

    private static final String IP = "203.0.113.45";

    private final IpHasher hasher = new IpHasher("test-pepper");

    @Test
    @DisplayName("PRIVACY: the stored value does not contain the address")
    void outputDoesNotContainTheAddress() {
        String hashed = hasher.hash(IP);

        assertThat(hashed).doesNotContain(IP).doesNotContain("203.0.113");
    }

    @Test
    @DisplayName("produces a 64-character hex digest")
    void producesHexDigest() {
        assertThat(hasher.hash(IP)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("is stable for the same address, so repeat visitors can be correlated")
    void stableForSameAddress() {
        // This is why a per-record random salt is unusable here: analytics needs the
        // same visitor to produce the same value.
        assertThat(hasher.hash(IP)).isEqualTo(hasher.hash(IP));
    }

    @Test
    @DisplayName("distinguishes different addresses")
    void differentAddressesDiffer() {
        assertThat(hasher.hash(IP)).isNotEqualTo(hasher.hash("198.51.100.7"));
    }

    @Test
    @DisplayName("SECURITY: the pepper actually participates in the digest")
    void pepperChangesTheResult() {
        // Guards the central protection. If the pepper were accepted but never mixed in,
        // every hash would be a plain SHA-256 of the address - and the whole 4.3-billion
        // IPv4 space can be precomputed in seconds, making the column reversible.
        String withOnePepper = new IpHasher("pepper-one").hash(IP);
        String withAnother = new IpHasher("pepper-two").hash(IP);

        assertThat(withOnePepper).isNotEqualTo(withAnother);
    }

    @Test
    @DisplayName("differs from an unpeppered SHA-256 of the same address")
    void differsFromPlainSha256() {
        // The concrete failure the pepper prevents: a value an attacker could reproduce
        // without knowing anything of ours.
        String plainSha256OfIp =
                "5a0dcbee9f8fcbb6d0b9ba4a68ad8c0d5b7a9cc7bd8e3c0f04d0e3e10ff2d14b";

        assertThat(hasher.hash(IP)).isNotEqualTo(plainSha256OfIp);
    }

    @Test
    @DisplayName("returns null when no address is available rather than hashing nothing")
    void nullForMissingAddress() {
        assertThat(hasher.hash(null)).isNull();
        assertThat(hasher.hash("")).isNull();
        assertThat(hasher.hash("   ")).isNull();
    }

    @Test
    @DisplayName("ignores surrounding whitespace, so one address yields one hash")
    void trimsBeforeHashing() {
        assertThat(hasher.hash("  " + IP + "  ")).isEqualTo(hasher.hash(IP));
    }

    @Test
    @DisplayName("refuses to start without a pepper rather than hashing unprotected")
    void rejectsMissingPepper() {
        // Failing startup is deliberate: silently continuing would produce a column that
        // looks protected but is trivially reversible.
        assertThatThrownBy(() -> new IpHasher("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IpHasher(null)).isInstanceOf(IllegalStateException.class);
    }
}
