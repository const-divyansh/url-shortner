package com.urlshortener.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves a hostname to its IP addresses.
 *
 * <p>Exists so that {@link com.urlshortener.validation.rule.TargetUrlHostSafetyRule}
 * depends on an abstraction rather than on {@link InetAddress} directly (Dependency
 * Inversion). Without it, every test of the SSRF rule would perform real DNS lookups -
 * slow, network-dependent, and dependent on records outside our control.
 */
public interface HostResolver {

    /**
     * @return every address the host resolves to; never empty
     * @throws UnknownHostException if the host cannot be resolved
     */
    InetAddress[] resolve(String host) throws UnknownHostException;
}
