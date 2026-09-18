package com.urlshortener;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.auth.IssuedSession;
import com.urlshortener.auth.OwnerSessionIssuer;
import com.urlshortener.entity.IdentityProvider;
import com.urlshortener.entity.Owner;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.OwnerRepository;
import com.urlshortener.repository.OwnerSessionRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.validation.HostResolver;

@SpringBootTest(classes = {
        UrlShortenerApplication.class,
        AbstractIntegrationTest.IntegrationTestConfig.class
}, properties = {
        "app.base-url=http://localhost:8080",
        "app.analytics.ip-pepper=test-pepper",
        "app.analytics.drain-interval-ms=60000",
        "app.oauth.google.client-id=test-client",
        "app.oauth.google.client-secret=test-secret",
        "app.oauth.google.redirect-uri=http://localhost:8080/api/auth/google/callback",
        "app.oauth.google.frontend-redirect-uri=http://localhost:5173/",
        "logging.level.io.lettuce.core.protocol.ConnectionWatchdog=ERROR",
        "logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF",
        "spring.data.redis.timeout=500ms"
})
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("urlshortener")
            .withUsername("urlshortener")
            .withPassword("postgres");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        Startables.deepStart(Stream.of(POSTGRES, REDIS)).join();
    }

    @DynamicPropertySource
    static void registerInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    protected UrlRepository urlRepository;
    @Autowired
    protected ClickEventRepository clickEventRepository;
    @Autowired
    protected OwnerRepository ownerRepository;
    @Autowired
    protected OwnerSessionRepository ownerSessionRepository;
    @Autowired
    protected OwnerSessionIssuer ownerSessionIssuer;
    @Autowired
    protected StringRedisTemplate redisTemplate;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() throws InterruptedException {
        flushRedisWithRetry();
        jdbcTemplate.execute("TRUNCATE TABLE click_events, owner_sessions, urls, owners RESTART IDENTITY CASCADE");
    }

    protected AuthenticatedPrincipal createGooglePrincipal() {
        String subject = "google-" + UUID.randomUUID();
        Owner owner = ownerRepository.saveAndFlush(
                Owner.fromProvider(IdentityProvider.GOOGLE, subject, subject + "@example.com"));
        return new AuthenticatedPrincipal(owner.getId(), owner.getProvider());
    }

    protected AuthenticatedPrincipal createGuestPrincipal() {
        Owner owner = ownerRepository.saveAndFlush(Owner.guest());
        return new AuthenticatedPrincipal(owner.getId(), owner.getProvider());
    }

    protected IssuedSession issueGoogleSession() {
        String subject = "google-" + UUID.randomUUID();
        return ownerSessionIssuer.issueForProviderIdentity(
                IdentityProvider.GOOGLE, subject, subject + "@example.com");
    }

    protected IssuedSession issueGuestSession() {
        return ownerSessionIssuer.issueForNewGuest();
    }

    protected static void waitFor(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private void flushRedisWithRetry() throws InterruptedException {
        RuntimeException lastFailure = null;

        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                Thread.sleep(250);
            }
        }

        throw lastFailure;
    }

    @TestConfiguration
    static class IntegrationTestConfig {

        @Bean
        @Primary
        HostResolver hostResolver() {
            return host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};
        }
    }
}
