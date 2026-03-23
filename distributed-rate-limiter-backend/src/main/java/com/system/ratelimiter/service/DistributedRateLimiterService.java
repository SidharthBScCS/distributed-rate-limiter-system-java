package com.system.ratelimiter.service;

import com.system.ratelimiter.dto.DecisionAuditEntry;
import com.system.ratelimiter.entity.ApiKey;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributedRateLimiterService {

    private static final String SLIDING_WINDOW = "SLIDING_WINDOW";
    private static final String STATUS_NORMAL = "Normal";
    private static final String STATUS_BLOCKED = "Blocked";

    private static final String SLIDING_WINDOW_SCRIPT = """
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])
            local requestId = ARGV[5]

            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - windowMs)
            local count = tonumber(redis.call('ZCARD', KEYS[1]))

            if count + cost > limit then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local oldestTs = now
                if oldest[2] ~= nil then oldestTs = tonumber(oldest[2]) end
                local retryMs = (oldestTs + windowMs) - now
                if retryMs < 1 then retryMs = 1 end
                local retrySeconds = math.ceil(retryMs / 1000.0)
            redis.call('PEXPIRE', KEYS[1], windowMs)
            return {0, retrySeconds, 2, count, limit}
            end

            for i = 1, cost do
                redis.call('ZADD', KEYS[1], now, requestId .. ':' .. i)
            end
            redis.call('PEXPIRE', KEYS[1], windowMs)
            return {1, 0, 0, count + cost, limit}
            """;

    private final StringRedisTemplate redisTemplate;
    private final ApiKeyService apiKeyService;
    private final RequestStatsService requestStatsService;
    private final DecisionAuditService decisionAuditService;
    private final MeterRegistry meterRegistry;
    private final String redisPrefix;
    private final DefaultRedisScript<List> slidingWindowScript;

    public DistributedRateLimiterService(
            StringRedisTemplate redisTemplate,
            ApiKeyService apiKeyService,
            RequestStatsService requestStatsService,
            DecisionAuditService decisionAuditService,
            MeterRegistry meterRegistry,
            @Value("${ratelimiter.redis-prefix:ratelimiter}") String redisPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.apiKeyService = apiKeyService;
        this.requestStatsService = requestStatsService;
        this.decisionAuditService = decisionAuditService;
        this.meterRegistry = meterRegistry;
        this.redisPrefix = redisPrefix == null || redisPrefix.isBlank() ? "ratelimiter" : redisPrefix.trim();

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(List.class);
    }

    @Transactional
    public Decision evaluate(String rawApiKey, String route, int tokens) {
        String apiKeyValue = rawApiKey == null ? "" : rawApiKey.trim();
        if (apiKeyValue.isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }

        ApiKey apiKey = apiKeyService.getCachedByValue(apiKeyValue);
        if (apiKey == null) {
            throw new IllegalArgumentException("API key not found");
        }

        int cost = Math.max(1, tokens);
        String routeKey = normalizeRoute(route);

        Decision decision;
        try {
            decision = slidingWindowDecision(apiKey, routeKey, cost);
        } catch (RedisSystemException ex) {
            throw new IllegalStateException("Rate limiter unavailable", ex);
        }

        updateStats(apiKey, decision.allowed());
        meterRegistry.counter(
                "ratelimiter_requests_total",
                "algorithm",
                SLIDING_WINDOW,
                "route",
                routeKey,
                "result",
                decision.allowed() ? "allowed" : "blocked"
        ).increment();
        decisionAuditService.record(new DecisionAuditEntry(
                apiKey.getApiKey(),
                routeKey,
                decision.allowed(),
                decision.reason(),
                decision.algorithm(),
                decision.retryAfterSeconds(),
                decision.limit(),
                decision.windowSeconds(),
                decision.currentUsage(),
                decision.remainingRequests(),
                decision.evaluatedAt().toString()
        ));

        return decision;
    }

    private Decision slidingWindowDecision(ApiKey apiKey, String route, int cost) {
        String windowKey = redisPrefix + ":sliding:" + apiKey.getApiKey() + ":" + route;
        List<Long> result = redisTemplate.execute(
                slidingWindowScript,
                List.of(windowKey),
                Long.toString(System.currentTimeMillis()),
                Long.toString(windowMs(apiKey)),
                Integer.toString(Math.max(1, apiKey.getRateLimit())),
                Integer.toString(cost),
                UUID.randomUUID().toString()
        );
        Decision baseDecision = toDecision(result);
        int limit = Math.max(1, apiKey.getRateLimit() == null ? 1 : apiKey.getRateLimit());
        int windowSeconds = Math.max(1, apiKey.getWindowSeconds() == null ? 60 : apiKey.getWindowSeconds());
        return new Decision(
                baseDecision.allowed(),
                baseDecision.retryAfterSeconds(),
                baseDecision.reason(),
                baseDecision.algorithm(),
                limit,
                windowSeconds,
                baseDecision.currentUsage(),
                Math.max(0L, (long) limit - baseDecision.currentUsage()),
                Instant.now(),
                route
        );
    }

    private Decision toDecision(List<Long> result) {
        if (result == null || result.size() < 5) {
            return new Decision(false, 1, "LIMITER_ERROR", SLIDING_WINDOW, 0, 0, 0L, 0L, Instant.now(), "global");
        }
        boolean allowed = result.get(0) != null && result.get(0) == 1L;
        int retryAfter = result.get(1) == null ? 1 : Math.max(1, result.get(1).intValue());
        long code = result.get(2) == null ? 4L : result.get(2);
        long currentUsage = result.get(3) == null ? 0L : Math.max(0L, result.get(3));
        int limit = result.get(4) == null ? 0 : Math.max(0, result.get(4).intValue());
        String reason = switch ((int) code) {
            case 0 -> "ALLOWED";
            case 2 -> "SLIDING_WINDOW_EXCEEDED";
            case 3 -> "LIMITER_ERROR";
            default -> "SLIDING_WINDOW_EXCEEDED";
        };
        long remainingRequests = Math.max(0L, (long) limit - currentUsage);
        return allowed
                ? new Decision(true, 0, reason, SLIDING_WINDOW, limit, 0, currentUsage, remainingRequests, Instant.now(), "global")
                : new Decision(false, retryAfter, reason, SLIDING_WINDOW, limit, 0, currentUsage, remainingRequests, Instant.now(), "global");
    }

    private void updateStats(ApiKey apiKey, boolean allowed) {
        if (allowed) {
            clearBlockMarker(apiKey.getApiKey());
        } else {
            setBlockMarker(apiKey.getApiKey(), apiKey.getWindowSeconds());
        }
        apiKeyService.recordDecision(apiKey.getApiKey(), allowed);

        if (allowed) {
            requestStatsService.incrementAllowed(1L);
        } else {
            requestStatsService.incrementBlocked(1L);
        }
    }

    public String resolveCurrentStatus(ApiKey apiKey) {
        if (apiKey == null || apiKey.getApiKey() == null || apiKey.getApiKey().isBlank()) {
            return STATUS_NORMAL;
        }
        return hasActiveBlockMarker(apiKey.getApiKey()) ? STATUS_BLOCKED : STATUS_NORMAL;
    }

    public long resolveCurrentWindowRequestCount(ApiKey apiKey) {
        if (apiKey == null || apiKey.getApiKey() == null || apiKey.getApiKey().isBlank()) {
            return 0L;
        }
        return Math.max(0L, sumSlidingWindowCount(apiKey.getApiKey(), windowMs(apiKey)));
    }

    private boolean hasActiveBlockMarker(String apiKeyValue) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockMarkerKey(apiKeyValue)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void setBlockMarker(String apiKeyValue, Integer windowSeconds) {
        try {
            Duration ttl = Duration.ofSeconds(Math.max(1, windowSeconds == null ? 60 : windowSeconds));
            redisTemplate.opsForValue().set(blockMarkerKey(apiKeyValue), "1", ttl);
        } catch (Exception ignored) {
            // Ignore marker failures; limiter decision has already been made.
        }
    }

    private void clearBlockMarker(String apiKeyValue) {
        try {
            redisTemplate.delete(blockMarkerKey(apiKeyValue));
        } catch (Exception ignored) {
            // Ignore marker cleanup failures.
        }
    }

    private String blockMarkerKey(String apiKeyValue) {
        return redisPrefix + ":status:block:" + apiKeyValue;
    }

    private long sumSlidingWindowCount(String apiKeyValue, long windowMs) {
        Set<String> keys;
        try {
            keys = redisTemplate.keys(redisPrefix + ":sliding:" + apiKeyValue + ":*");
        } catch (Exception ex) {
            return 0L;
        }
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long minScore = Math.max(0L, now - Math.max(1L, windowMs));
        long sum = 0L;
        for (String key : keys) {
            try {
                redisTemplate.opsForZSet().removeRangeByScore(key, 0, minScore);
                Long value = redisTemplate.opsForZSet().zCard(key);
                sum += value == null ? 0L : value;
            } catch (Exception ignored) {
                // Ignore per-key lookup failures and continue summing the rest.
            }
        }
        return sum;
    }

    private long windowMs(ApiKey apiKey) {
        return Math.max(1, apiKey.getWindowSeconds() == null ? 60 : apiKey.getWindowSeconds()) * 1000L;
    }

    private String normalizeRoute(String route) {
        String value = route == null ? "global" : route.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "global";
        }
        return value.replace(' ', '_');
    }

    public record Decision(
            boolean allowed,
            int retryAfterSeconds,
            String reason,
            String algorithm,
            int limit,
            int windowSeconds,
            long currentUsage,
            long remainingRequests,
            Instant evaluatedAt,
            String route
    ) {}
}
