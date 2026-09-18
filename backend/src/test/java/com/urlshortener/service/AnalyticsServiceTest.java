package com.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.urlshortener.auth.LinkAuthorizationPolicy;
import com.urlshortener.config.AppProperties;
import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.OwnedUrlSummaryProjection;
import com.urlshortener.dto.OwnedUrlSummaryResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.OwnershipForbiddenException;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String CODE = "abc1234";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final long OWNER_ID = 7L;
    private static final long OTHER_OWNER_ID = 8L;
    private static final String BASE_URL = "http://localhost:8080";

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private ClickEventRepository clickRepository;

    private AnalyticsService service() {
        return new AnalyticsService(urlRepository, clickRepository, new LinkAuthorizationPolicy(),
                new AppProperties(BASE_URL));
    }

    private void urlExists(Url url) {
        when(urlRepository.findByShortCode(CODE)).thenReturn(Optional.of(url));
        when(clickRepository.findByUrlIdOrderByOccurredAtDesc(any(), any())).thenReturn(List.of());
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clickRepository).findByUrlIdOrderByOccurredAtDesc(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("unknown code is a 404")
    void unknownCodeIsNotFound() {
        when(urlRepository.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().forShortCode(OWNER_ID, CODE, 0, 20))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    @DisplayName("an expired link still reports its statistics")
    void expiredLinkStillReportsStatistics() {
        // The link stops redirecting, but its history remains meaningful and is not
        // deleted - so only a genuinely unknown code is a 404.
        urlExists(new Url(CODE, "https://example.com", NOW.minusSeconds(60), OWNER_ID));
        when(clickRepository.countByUrlId(any())).thenReturn(7L);

        AnalyticsResponse response = service().forShortCode(OWNER_ID, CODE, 0, 20);

        assertThat(response.totalClicks()).isEqualTo(7L);
    }

    @Test
    @DisplayName("reports the overall total, not just the size of the returned page")
    void totalIsIndependentOfPagination() {
        urlExists(new Url(CODE, "https://example.com", null, OWNER_ID));
        when(clickRepository.countByUrlId(any())).thenReturn(500L);

        AnalyticsResponse response = service().forShortCode(OWNER_ID, CODE, 0, 20);

        assertThat(response.totalClicks()).isEqualTo(500L);
        assertThat(response.clicks()).isEmpty();
    }

    @Test
    @DisplayName("caps page size so a caller cannot demand an unbounded query")
    void capsPageSize() {
        urlExists(new Url(CODE, "https://example.com", null, OWNER_ID));

        service().forShortCode(OWNER_ID, CODE, 0, 100_000);

        assertThat(capturePageable().getPageSize()).isEqualTo(AnalyticsService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("coerces a non-positive page size to a usable value")
    void coercesNonPositivePageSize() {
        urlExists(new Url(CODE, "https://example.com", null, OWNER_ID));

        service().forShortCode(OWNER_ID, CODE, 0, 0);

        assertThat(capturePageable().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("coerces a negative page number instead of failing")
    void coercesNegativePage() {
        // PageRequest.of rejects a negative page, so an unchecked value would surface as
        // a 500 rather than being treated as the first page.
        urlExists(new Url(CODE, "https://example.com", null, OWNER_ID));

        AnalyticsResponse response = service().forShortCode(OWNER_ID, CODE, -5, 20);

        assertThat(response.page()).isZero();
        assertThat(capturePageable().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("reports last-accessed from a dedicated aggregate")
    void reportsLastClickAt() {
        urlExists(new Url(CODE, "https://example.com", null, OWNER_ID));
        when(clickRepository.findLastClickAt(any())).thenReturn(NOW);

        assertThat(service().forShortCode(OWNER_ID, CODE, 0, 20).lastClickAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a never-clicked link reports no last-accessed time")
    void neverClickedHasNoLastClick() {
        urlExists(new Url(CODE, "https://example.com", null, OWNER_ID));
        when(clickRepository.findLastClickAt(any())).thenReturn(null);

        AnalyticsResponse response = service().forShortCode(OWNER_ID, CODE, 0, 20);

        assertThat(response.lastClickAt()).isNull();
        assertThat(response.totalClicks()).isZero();
    }

    @Test
    @DisplayName("analytics are forbidden for a link owned by another caller")
    void ownerMismatchIsForbidden() {
        when(urlRepository.findByShortCode(CODE))
                .thenReturn(Optional.of(new Url(CODE, "https://example.com", null, OTHER_OWNER_ID)));

        assertThatThrownBy(() -> service().forShortCode(OWNER_ID, CODE, 0, 20))
                .isInstanceOf(OwnershipForbiddenException.class);
    }

    @Test
    @DisplayName("lists previously created links for the authenticated owner, with the short URL built in")
    void listsOwnedLinks() {
        List<OwnedUrlSummaryProjection> projections = List.of(
                new OwnedUrlSummaryProjection(CODE, "https://example.com", NOW, null, 12L, NOW));
        when(urlRepository.findOwnedSummaries(OWNER_ID)).thenReturn(projections);

        assertThat(service().listOwnedLinks(OWNER_ID)).containsExactly(
                new OwnedUrlSummaryResponse(CODE, BASE_URL + "/" + CODE, "https://example.com", NOW, null, 12L, NOW));
    }
}
