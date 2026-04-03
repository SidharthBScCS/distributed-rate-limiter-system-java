package com.system.ratelimiter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.ratelimiter.dto.DecisionAuditEntry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionAuditServiceTest {

    @Test
    void recordBuffersEntriesAndFlushesThemInBatch() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(redisTemplate.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any(DecisionAuditEntry.class)))
                .thenReturn("{\"decision\":1}", "{\"decision\":2}");

        DecisionAuditService service = new DecisionAuditService(
                redisTemplate,
                objectMapper,
                "ratelimiter",
                200,
                200,
                5000,
                true
        );

        service.record(entry(false, "blocked-1"));
        service.record(entry(true, "allowed-1"));
        service.flushPending();

        verify(redisTemplate, times(1)).executePipelined(any(RedisCallback.class));
    }

    @Test
    void blockedDecisionListenersStillRunImmediatelyWhenEntryIsBuffered() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(redisTemplate.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(objectMapper.writeValueAsString(any(DecisionAuditEntry.class))).thenReturn("{\"decision\":1}");

        DecisionAuditService service = new DecisionAuditService(
                redisTemplate,
                objectMapper,
                "ratelimiter",
                200,
                200,
                5000,
                true
        );

        AtomicReference<DecisionAuditEntry> observed = new AtomicReference<>();
        service.registerBlockedDecisionListener(observed::set);

        DecisionAuditEntry blocked = entry(false, "blocked-now");
        service.record(blocked);

        assertNotNull(observed.get());
        assertEquals("blocked-now", observed.get().apiKey());
    }

    private DecisionAuditEntry entry(boolean allowed, String apiKey) {
        return new DecisionAuditEntry(
                apiKey,
                "global",
                allowed,
                allowed ? "ALLOWED" : "SLIDING_WINDOW_EXCEEDED",
                "SLIDING_WINDOW",
                allowed ? 0 : 1,
                10,
                60,
                allowed ? 3 : 10,
                allowed ? 7 : 0,
                "2026-03-28T00:00:00Z"
        );
    }
}
