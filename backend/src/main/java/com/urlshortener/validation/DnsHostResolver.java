package com.urlshortener.validation;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

/**
 * Default {@link HostResolver}, backed by the platform DNS resolver.
 */
@Component
public class DnsHostResolver implements HostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
