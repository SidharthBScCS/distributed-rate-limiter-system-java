package com.system.ratelimiter.service;

import com.system.ratelimiter.dto.DecisionAuditEntry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class DecisionAuditService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String auditKey;
    private final long maxEntries;
    private final boolean enabled;

    public DecisionAuditService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${ratelimiter.redis-prefix:ratelimiter}") String redisPrefix,
            @Value("${ratelimiter.audit.max-entries:200}") long maxEntries,
            @Value("${ratelimiter.audit.enabled:true}") boolean enabled
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        String prefix = redisPrefix == null || redisPrefix.isBlank() ? "ratelimiter" : redisPrefix.trim();
        this.auditKey = prefix + ":audit:recent";
        this.maxEntries = Math.max(20L, maxEntries);
        this.enabled = enabled;
    }

    public void record(DecisionAuditEntry entry) {
        if (!enabled || entry == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForList().leftPush(auditKey, payload);
            redisTemplate.opsForList().trim(auditKey, 0, maxEntries - 1);
        } catch (Exception ignored) {
            // Audit logging is best-effort and must not impact limiter decisions.
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
                // Skip malformed audit entries instead of failing the endpoint.
            }
        }
        return entries;
    }
}
