package com.system.ratelimiter.service;

import com.system.ratelimiter.dto.DecisionAuditEntry;
import com.system.ratelimiter.entity.ApiKey;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
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
            redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, now - windowMs)
            local count = tonumber(redis.call('ZCARD', KEYS[1]))

            if count + cost > limit then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local oldestTs = now
                if oldest[2] ~= nil then oldestTs = tonumber(oldest[2]) end
                local retryMs = (oldestTs + windowMs) - now
                if retryMs < 1 then retryMs = 1 end
                local retrySeconds = math.ceil(retryMs / 1000.0)
                redis.call('PEXPIRE', KEYS[1], windowMs)
                redis.call('PEXPIRE', KEYS[2], windowMs)
                redis.call('PSETEX', KEYS[3], retryMs, '1')
                return {0, retrySeconds, 2, count, limit}
            end

            for i = 1, cost do
                local member = requestId .. ':' .. i
                redis.call('ZADD', KEYS[1], now, member)
                redis.call('ZADD', KEYS[2], now, member)
            end
            redis.call('PEXPIRE', KEYS[1], windowMs)
            redis.call('PEXPIRE', KEYS[2], windowMs)
            redis.call('DEL', KEYS[3])
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
        String apiKeyValue = apiKey.getApiKey();
        String windowKey = routeWindowKey(apiKeyValue, route);
        List<Long> result = redisTemplate.execute(
                slidingWindowScript,
                List.of(
                        windowKey,
                        globalWindowKey(apiKeyValue),
                        blockMarkerKey(apiKeyValue)
                ),
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
        return Math.max(0L, currentWindowCount(apiKey.getApiKey(), windowMs(apiKey)));
    }

    public Map<String, DashboardLiveSnapshot> snapshotDashboardState(List<ApiKey> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return Map.of();
        }

        List<ApiKey> validKeys = apiKeys.stream()
                .filter(apiKey -> apiKey != null && apiKey.getApiKey() != null && !apiKey.getApiKey().isBlank())
                .toList();
        if (validKeys.isEmpty()) {
            return Map.of();
        }

        List<Object> pipelineResults;
        try {
            pipelineResults = redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) {
                    long now = System.currentTimeMillis();
                    for (ApiKey apiKey : validKeys) {
                        String apiKeyValue = apiKey.getApiKey();
                        operations.hasKey(blockMarkerKey(apiKeyValue));
                        operations.opsForZSet().count(
                                globalWindowKey(apiKeyValue),
                                Math.max(0L, now - Math.max(1L, windowMs(apiKey))),
                                Double.POSITIVE_INFINITY
                        );
                    }
                    return null;
                }
            });
        } catch (Exception ex) {
            Map<String, DashboardLiveSnapshot> fallback = new LinkedHashMap<>();
            for (ApiKey apiKey : validKeys) {
                fallback.put(apiKey.getApiKey(), new DashboardLiveSnapshot(
                        resolveCurrentStatus(apiKey),
                        resolveCurrentWindowRequestCount(apiKey)
                ));
            }
            return fallback;
        }

        Map<String, DashboardLiveSnapshot> snapshots = new LinkedHashMap<>();
        int index = 0;
        for (ApiKey apiKey : validKeys) {
            Object statusResult = index < pipelineResults.size() ? pipelineResults.get(index++) : null;
            Object countResult = index < pipelineResults.size() ? pipelineResults.get(index++) : null;
            String status = isTruthy(statusResult) ? STATUS_BLOCKED : STATUS_NORMAL;
            long requestCount = Math.max(0L, toLong(countResult));
            snapshots.put(apiKey.getApiKey(), new DashboardLiveSnapshot(status, requestCount));
        }
        return snapshots;
    }

    private boolean hasActiveBlockMarker(String apiKeyValue) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockMarkerKey(apiKeyValue)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String blockMarkerKey(String apiKeyValue) {
        return redisPrefix + ":status:block:" + apiKeyValue;
    }

    private String globalWindowKey(String apiKeyValue) {
        return redisPrefix + ":sliding:" + apiKeyValue + ":global";
    }

    private String routeWindowKey(String apiKeyValue, String route) {
        return redisPrefix + ":sliding:" + apiKeyValue + ":" + route;
    }

    private long currentWindowCount(String apiKeyValue, long windowMs) {
        String key = globalWindowKey(apiKeyValue);
        try {
            long now = System.currentTimeMillis();
            long minScore = Math.max(0L, now - Math.max(1L, windowMs));
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, minScore);
            Long count = redisTemplate.opsForZSet().zCard(key);
            return count == null ? 0L : count;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private long windowMs(ApiKey apiKey) {
        return Math.max(1, apiKey.getWindowSeconds() == null ? 60 : apiKey.getWindowSeconds()) * 1000L;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.longValue() > 0L;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
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

    public record DashboardLiveSnapshot(
            String status,
            long requestCount
    ) {}
}
