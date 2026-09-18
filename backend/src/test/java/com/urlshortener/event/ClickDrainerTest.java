package com.urlshortener.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;

@ExtendWith(MockitoExtension.class)
class ClickDrainerTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private ClickBuffer buffer;
    @Mock
    private ClickEventRepository repository;
    @Mock
    private ClickBroadcaster broadcaster;

    private ClickDrainer drainer() {
        return new ClickDrainer(buffer, repository, broadcaster, 100);
    }

    @SuppressWarnings("unchecked")
    private List<ClickEvent> captureSaved() {
        ArgumentCaptor<List<ClickEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("does not touch the database when nothing is buffered")
    void skipsEmptyBatches() {
        when(buffer.drain(anyInt())).thenReturn(List.of());

        drainer().drain();

        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("persists a drained batch in one call")
    void persistsBatch() {
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, 10L, WHEN, "hash-a", "ref-a", "ua-a"),
                new ClickRecord(2L, 20L, WHEN, "hash-b", "ref-b", "ua-b")));

        drainer().drain();

        assertThat(captureSaved()).hasSize(2)
                .extracting(ClickEvent::getUrlId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("truncates oversized headers so one bad value cannot fail the batch")
    void truncatesOversizedHeaders() {
        // Referrer and user-agent are caller-supplied and unbounded. Without truncation
        // a single long value would breach the column width and reject every click in
        // the same insert.
        when(buffer.drain(anyInt())).thenReturn(List.of(new ClickRecord(
                1L, 10L, WHEN, "hash", "r".repeat(5000), "u".repeat(5000))));

        drainer().drain();

        ClickEvent saved = captureSaved().get(0);
        assertThat(saved.getReferrer()).hasSize(2048);
        assertThat(saved.getUserAgent()).hasSize(512);
    }

    @Test
    @DisplayName("leaves values within the limit untouched")
    void keepsShortHeadersIntact() {
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, 10L, WHEN, "hash", "https://news.example/", "Mozilla/5.0")));

        drainer().drain();

        ClickEvent saved = captureSaved().get(0);
        assertThat(saved.getReferrer()).isEqualTo("https://news.example/");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("tolerates a missing address, since it may be unavailable")
    void toleratesNullFields() {
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, 10L, WHEN, null, null, null)));

        drainer().drain();

        assertThat(captureSaved().get(0).getIpHash()).isNull();
    }

    @Test
    @DisplayName("a failed batch does not stop the scheduler from running again")
    void swallowsPersistenceFailure() {
        // Rethrowing would have the scheduler log the same failure a second time while
        // recovering nothing: the batch has already left Redis.
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, 10L, WHEN, "hash", null, null)));
        when(repository.saveAll(anyList())).thenThrow(new RuntimeException("database down"));

        assertThatCode(() -> drainer().drain()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("broadcasts to each distinct owner only after the batch is persisted")
    void broadcastsAfterPersist() {
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, 10L, WHEN, "hash-a", null, null),
                new ClickRecord(2L, 10L, WHEN, "hash-b", null, null),
                new ClickRecord(3L, 20L, WHEN, "hash-c", null, null)));

        drainer().drain();

        verify(broadcaster).broadcast(10L);
        verify(broadcaster).broadcast(20L);
    }

    @Test
    @DisplayName("does not broadcast when persistence fails")
    void doesNotBroadcastOnPersistenceFailure() {
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, 10L, WHEN, "hash", null, null)));
        when(repository.saveAll(anyList())).thenThrow(new RuntimeException("database down"));

        drainer().drain();

        verify(broadcaster, never()).broadcast(any());
    }

    @Test
    @DisplayName("does not broadcast when the drained batch has no owned url")
    void skipsBroadcastForNullOwner() {
        when(buffer.drain(anyInt())).thenReturn(List.of(
                new ClickRecord(1L, null, WHEN, "hash", null, null)));

        drainer().drain();

        verify(broadcaster, never()).broadcast(any());
    }
}
