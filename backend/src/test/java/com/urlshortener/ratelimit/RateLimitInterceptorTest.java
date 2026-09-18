package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.urlshortener.event.ClientIpResolver;
import com.urlshortener.event.IpHasher;
import com.urlshortener.exception.RateLimitExceededException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitInterceptorTest {

    private static final String RAW_IP = "203.0.113.7";
    private static final String HASHED_IP = "hashed-203-0-113-7";

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private IpHasher ipHasher;

    private final RateLimitKeyNormaliser keyNormaliser = new RateLimitKeyNormaliser();

    private RateLimitInterceptor redirectInterceptor;

    @BeforeEach
    void setUp() {
        when(clientIpResolver.resolve(any())).thenReturn(RAW_IP);
        when(ipHasher.hash(RAW_IP)).thenReturn(HASHED_IP);
        when(rateLimiter.check(any(), anyString())).thenReturn(RateLimitDecision.allow());

        redirectInterceptor = new RateLimitInterceptor(
                rateLimiter, clientIpResolver, keyNormaliser, ipHasher)
                .withScope(RateLimitScope.REDIRECT, HttpMethod.GET);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    @Test
    @DisplayName("allows a request the limiter permits")
    void allowsPermittedRequest() {
        boolean proceed = redirectInterceptor.preHandle(
                request("GET", "/abc1234"), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("raises 429 as an exception so the central handler shapes the response")
    void rejectedRequestThrows() {
        // Thrown rather than written here, so the 429 body matches every other error in
        // the API. Writing the response directly would create a second error format no
        // controller advice governs.
        when(rateLimiter.check(any(), anyString()))
                .thenReturn(RateLimitDecision.reject(Duration.ofSeconds(42)));

        assertThatThrownBy(() -> redirectInterceptor.preHandle(
                request("GET", "/abc1234"), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfter())
                        .isEqualTo(Duration.ofSeconds(42)));
    }

    @Test
    @DisplayName("counts the HASHED address, never the raw one")
    void countsHashedAddress() {
        // Rate limiting must introduce no new privacy surface: the same guarantee that
        // covers stored analytics (NFR8) has to cover these counters too.
        redirectInterceptor.preHandle(
                request("GET", "/abc1234"), new MockHttpServletResponse(), new Object());

        verify(ipHasher).hash(RAW_IP);
        verify(rateLimiter).check(RateLimitScope.REDIRECT, HASHED_IP);
        verify(rateLimiter, never()).check(any(), org.mockito.ArgumentMatchers.eq(RAW_IP));
    }

    @Test
    @DisplayName("REGRESSION: does not count a CORS preflight")
    void doesNotCountPreflight() {
        // A preflight is the browser asking permission, not the request itself.
        // Counting it would halve every browser client's effective budget and surface a
        // rejection as an opaque network error rather than a 429.
        boolean proceed = redirectInterceptor.preHandle(
                request("OPTIONS", "/abc1234"), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("REGRESSION: the create budget ignores GET on the same path")
    void createBudgetIgnoresListing() {
        // /api/urls serves both creation (POST) and the owned-links listing (GET).
        // Charging a cheap authenticated read against the creation budget would let
        // simply refreshing the analytics screen exhaust the ability to create links.
        RateLimitInterceptor createInterceptor =
                redirectInterceptor.withScope(RateLimitScope.CREATE, HttpMethod.POST);

        boolean proceed = createInterceptor.preHandle(
                request("GET", "/api/urls"), new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("the create budget does count POST on that path")
    void createBudgetCountsCreation() {
        RateLimitInterceptor createInterceptor =
                redirectInterceptor.withScope(RateLimitScope.CREATE, HttpMethod.POST);

        createInterceptor.preHandle(
                request("POST", "/api/urls"), new MockHttpServletResponse(), new Object());

        verify(rateLimiter).check(RateLimitScope.CREATE, HASHED_IP);
    }

    @Test
    @DisplayName("REGRESSION: loopback over IPv4 and IPv6 shares one budget")
    void loopbackSharesOneBudgetAcrossFamilies() {
        // The defect found in manual testing: a configured limit appeared not to work
        // locally because the browser and curl arrived over different IP families, so
        // one machine held two budgets. Asserted here, on the request path, because the
        // normaliser being correct in isolation does not prove it is actually wired in.
        when(ipHasher.hash(anyString())).thenAnswer(i -> "hash-of-" + i.getArgument(0));

        when(clientIpResolver.resolve(any())).thenReturn("127.0.0.1");
        redirectInterceptor.preHandle(
                request("GET", "/abc1234"), new MockHttpServletResponse(), new Object());

        when(clientIpResolver.resolve(any())).thenReturn("::1");
        redirectInterceptor.preHandle(
                request("GET", "/abc1234"), new MockHttpServletResponse(), new Object());

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, org.mockito.Mockito.times(2))
                .check(org.mockito.ArgumentMatchers.eq(RateLimitScope.REDIRECT), keys.capture());

        assertThat(keys.getAllValues()).hasSize(2);
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
    }

    @Test
    @DisplayName("a no-op limiter lets everything through")
    void noOpLimiterAllowsEverything() {
        RateLimitInterceptor disabled = new RateLimitInterceptor(
                new NoOpRateLimiter(), clientIpResolver, keyNormaliser, ipHasher)
                .withScope(RateLimitScope.REDIRECT, HttpMethod.GET);

        for (int i = 0; i < 100; i++) {
            assertThat(disabled.preHandle(
                    request("GET", "/abc1234"), new MockHttpServletResponse(), new Object()))
                    .isTrue();
        }
    }
}
