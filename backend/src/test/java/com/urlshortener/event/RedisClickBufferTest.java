package com.urlshortener.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class RedisClickBufferTest {

    private static final String KEY = "clicks:test";
    private static final ClickRecord RECORD =
            new ClickRecord(1L, 10L, Instant.parse("2026-01-01T12:00:00Z"), "hash", "ref", "ua");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private StringRedisTemplate redis;
    private ListOperations<String, String> listOps;
    private RedisClickBuffer buffer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        buffer = new RedisClickBuffer(redis, objectMapper, KEY);
    }

    @Test
    @DisplayName("appends to the right so draining from the left yields FIFO order")
    void enqueueAppendsToTheRight() {
        buffer.enqueue(RECORD);

        org.mockito.Mockito.verify(listOps).rightPush(org.mockito.ArgumentMatchers.eq(KEY), anyString());
    }

    @Test
    @DisplayName("CONTRACT: enqueue never throws when the buffer is unreachable")
    void enqueueSwallowsBufferFailure() {
        // The interface promises this, and a redirect depends on it: the visitor has
        // already been served, so a buffering failure must not become their error.
        when(listOps.rightPush(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> buffer.enqueue(RECORD)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("round-trips a record through serialisation")
    void drainReturnsEnqueuedRecords() throws Exception {
        String payload = objectMapper.writeValueAsString(RECORD);
        when(listOps.leftPop(KEY, 10)).thenReturn(List.of(payload));

        assertThat(buffer.drain(10)).containsExactly(RECORD);
    }

    @Test
    @DisplayName("returns empty rather than failing when the buffer is unreachable")
    void drainSwallowsBufferFailure() {
        when(listOps.leftPop(anyString(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(buffer.drain(10)).isEmpty();
    }

    @Test
    void drainHandlesEmptyBuffer() {
        when(listOps.leftPop(KEY, 10)).thenReturn(null);

        assertThat(buffer.drain(10)).isEmpty();
    }

    @Test
    @DisplayName("one unreadable entry does not discard the rest of the batch")
    void drainSkipsUnreadablePayloads() throws Exception {
        String valid = objectMapper.writeValueAsString(RECORD);
        when(listOps.leftPop(KEY, 10)).thenReturn(List.of("{not json", valid));

        assertThat(buffer.drain(10)).containsExactly(RECORD);
    }
}
