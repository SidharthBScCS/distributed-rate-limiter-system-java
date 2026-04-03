package com.system.ratelimiter.service;

import com.system.ratelimiter.dto.DecisionAuditEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DecisionAuditService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String auditKey;
    private final long maxEntries;
    private final boolean enabled;
    private final int batchSize;
    private final int queueCapacity;
    private final CopyOnWriteArrayList<Consumer<DecisionAuditEntry>> blockedDecisionListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedDeque<String> pendingAuditPayloads = new ConcurrentLinkedDeque<>();
    private final AtomicInteger pendingAuditCount = new AtomicInteger();

    public DecisionAuditService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${ratelimiter.redis-prefix:ratelimiter}") String redisPrefix,
            @Value("${ratelimiter.audit.max-entries:200}") long maxEntries,
            @Value("${ratelimiter.audit.batch-size:200}") int batchSize,
            @Value("${ratelimiter.audit.queue-capacity:5000}") int queueCapacity,
            @Value("${ratelimiter.audit.enabled:true}") boolean enabled
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        String prefix = redisPrefix == null || redisPrefix.isBlank() ? "ratelimiter" : redisPrefix.trim();
        this.auditKey = prefix + ":audit:recent";
        this.maxEntries = Math.max(20L, maxEntries);
        this.batchSize = Math.max(10, batchSize);
        this.queueCapacity = Math.max(this.batchSize, queueCapacity);
        this.enabled = enabled;
    }

    public void record(DecisionAuditEntry entry) {
        if (!enabled || entry == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(entry);
            enqueue(payload);
            notifyBlockedDecision(entry);
        } catch (Exception ignored) {
            
        }
    }

    @Scheduled(fixedDelayString = "${ratelimiter.audit.flush-ms:1000}")
    public void flushPending() {
        if (!enabled || pendingAuditPayloads.isEmpty()) {
            return;
        }

        List<String> batch = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            String payload = pendingAuditPayloads.pollLast();
            if (payload == null) {
                break;
            }
            pendingAuditCount.decrementAndGet();
            batch.add(payload);
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            byte[] auditKeyBytes = redisTemplate.getStringSerializer().serialize(auditKey);
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                pushBatch(connection, auditKeyBytes, batch);
                return null;
            });
        } catch (Exception ex) {
            // Put the batch back at the tail so it can be retried in order later.
            for (int i = batch.size() - 1; i >= 0; i--) {
                pendingAuditPayloads.offerLast(batch.get(i));
                pendingAuditCount.incrementAndGet();
            }
            trimQueueIfNeeded();
        }
    }

    public void registerBlockedDecisionListener(Consumer<DecisionAuditEntry> listener) {
        if (listener != null) {
            blockedDecisionListeners.add(listener);
        }
    }

    public List<DecisionAuditEntry> getRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, (int) maxEntries));
        List<String> values;
        try {
            values = redisTemplate.opsForList().range(auditKey, 0, safeLimit - 1);
        } catch (Exception ex) {
            return List.of();
        }
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<DecisionAuditEntry> entries = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                entries.add(objectMapper.readValue(value, DecisionAuditEntry.class));
            } catch (Exception ignored) {
               
            }
        }
        return entries;
    }

    @PreDestroy
    void flushOnShutdown() {
        flushPending();
    }

    private void enqueue(String payload) {
        pendingAuditPayloads.offerFirst(payload);
        pendingAuditCount.incrementAndGet();
        trimQueueIfNeeded();
    }

    private void trimQueueIfNeeded() {
        while (pendingAuditCount.get() > queueCapacity) {
            if (pendingAuditPayloads.pollLast() == null) {
                return;
            }
            pendingAuditCount.decrementAndGet();
        }
    }

    private void pushBatch(RedisConnection connection, byte[] auditKeyBytes, List<String> batch) throws DataAccessException {
        for (String payload : batch) {
            byte[] payloadBytes = redisTemplate.getStringSerializer().serialize(payload);
            if (payloadBytes != null) {
                connection.listCommands().lPush(auditKeyBytes, payloadBytes);
            }
        }
        connection.listCommands().lTrim(auditKeyBytes, 0, maxEntries - 1);
    }

    private void notifyBlockedDecision(DecisionAuditEntry entry) {
        if (entry.allowed()) {
            return;
        }
        for (Consumer<DecisionAuditEntry> listener : blockedDecisionListeners) {
            try {
                listener.accept(entry);
            } catch (Exception ignored) {
               
            }
        }
    }
}
