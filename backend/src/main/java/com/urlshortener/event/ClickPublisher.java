package com.urlshortener.event;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.urlshortener.entity.Url;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds a {@link ClickRecord} from a served redirect and announces it.
 *
 * <p><strong>This is where the privacy guarantee is enforced (NFR8).</strong> The client
 * address is resolved and hashed here, before the record exists. Nothing downstream -
 * the event, the Redis buffer, the database - ever receives the raw address. Hashing
 * later in the pipeline would leave the plain address sitting in Redis, which would make
 * {@link IpHasher} decorative rather than protective.
 *
 * <p>Separate from the controller so this logic is unit-testable without a web layer,
 * and so there is exactly one place where an address is converted.
 */
@Component
public class ClickPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickPublisher.class);

    private final ApplicationEventPublisher events;
    private final ClientIpResolver ipResolver;
    private final IpHasher ipHasher;
    private final Clock clock;

    public ClickPublisher(ApplicationEventPublisher events,
                          ClientIpResolver ipResolver,
                          IpHasher ipHasher,
                          Clock clock) {
        this.events = events;
        this.ipResolver = ipResolver;
        this.ipHasher = ipHasher;
        this.clock = clock;
    }

    /**
     * Records that {@code url} was served to the caller of {@code request}.
     *
     * <p>Never throws. The visitor has already been redirected successfully, so a
     * failure here is an analytics problem, not theirs - it is logged and swallowed
     * rather than converted into an error the visitor would see.
     */
    public void publish(Url url, HttpServletRequest request) {
        if (isSpeculative(request)) {
            return;
        }
        try {
            String ipHash = ipHasher.hash(ipResolver.resolve(request));

            events.publishEvent(new UrlRedirectedEvent(new ClickRecord(
                    url.getId(),
                    url.getOwnerId(),
                    clock.instant(),
                    ipHash,
                    request.getHeader("Referer"),
                    request.getHeader("User-Agent"))));
        } catch (RuntimeException e) {
            log.warn("Could not record click for '{}' ({})", url.getShortCode(), e.getMessage());
        }
    }

    /**
     * True for a request that never represents a genuine visit, so it must not be
     * counted even though {@link com.urlshortener.controller.RedirectController}'s
     * {@code @GetMapping} still runs for it and still needs to answer with the correct
     * redirect.
     *
     * <p><strong>HEAD.</strong> Spring MVC transparently dispatches a HEAD request to
     * the same handler registered for GET, running the full method body - including
     * this call - and only suppressing the response body afterwards. Browsers, link
     * previewers, and security/link scanners routinely send a HEAD probe before (or
     * instead of) a real navigation; without this check, every one of those silently
     * inflated the click count for a page nobody actually visited.
     *
     * <p><strong>Prefetch.</strong> A browser's speculative prefetch (Chrome's
     * NoState-Prefetch, the Speculation Rules API, etc.) is a real GET, so the HTTP
     * method alone cannot distinguish it - it self-identifies via the
     * {@code Sec-Purpose}/{@code Purpose} request header instead.
     */
    private static boolean isSpeculative(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String purpose = request.getHeader("Sec-Purpose");
        if (purpose == null) {
            purpose = request.getHeader("Purpose");
        }
        return purpose != null && purpose.toLowerCase(Locale.ROOT).contains("prefetch");
    }
}
