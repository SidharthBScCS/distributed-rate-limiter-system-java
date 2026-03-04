package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.repository.ApiKeyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
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

    private static final String FIXED_WINDOW = "FIXED_WINDOW";
    private static final String SLIDING_WINDOW = "SLIDING_WINDOW";
    private static final String TOKEN_BUCKET = "TOKEN_BUCKET";

    private static final String FIXED_WINDOW_SCRIPT = """
            local now = tonumber(ARGV[1])
            local windowMs = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])

            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if current + cost > limit then
                local elapsed = now % windowMs
                local retryMs = windowMs - elapsed
                if retryMs < 1 then retryMs = 1 end
                local retrySeconds = math.ceil(retryMs / 1000.0)
                return {0, retrySeconds, 1}
            end

            local updated = redis.call('INCRBY', KEYS[1], cost)
            if updated == cost then
                redis.call('PEXPIRE', KEYS[1], windowMs)
            end
            return {1, 0, 0}
            """;

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
                return {0, retrySeconds, 2}
            end

            for i = 1, cost do
                redis.call('ZADD', KEYS[1], now, requestId .. ':' .. i)
            end
            redis.call('PEXPIRE', KEYS[1], windowMs)
            return {1, 0, 0}
            """;

    private static final String TOKEN_BUCKET_SCRIPT = """
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refillPerMs = tonumber(ARGV[3])
            local cost = tonumber(ARGV[4])

            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens'))
            local last = tonumber(redis.call('HGET', KEYS[1], 'last_ms'))

            if tokens == nil then tokens = capacity end
            if last == nil then last = now end

            if now > last then
                local delta = now - last
                tokens = math.min(capacity, tokens + (delta * refillPerMs))
            end

            local ttlMs = math.ceil(capacity / refillPerMs)
            if ttlMs < 1000 then ttlMs = 1000 end

            if tokens < cost then
                local needed = cost - tokens
                local retryMs = math.ceil(needed / refillPerMs)
                if retryMs < 1 then retryMs = 1 end
                redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_ms', now)
                redis.call('PEXPIRE', KEYS[1], ttlMs)
                return {0, math.ceil(retryMs / 1000.0), 3}
            end

            tokens = tokens - cost
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_ms', now)
            redis.call('PEXPIRE', KEYS[1], ttlMs)
            return {1, 0, 0}
            """;

    private final StringRedisTemplate redisTemplate;
    private final ApiKeyRepository apiKeyRepository;
    private final RequestStatsService requestStatsService;
    private final MeterRegistry meterRegistry;
    private final String redisPrefix;
    private final double tokenBucketRefillPerSecond;
    private final double tokenBucketCapacityMultiplier;
    private final DefaultRedisScript<List> fixedWindowScript;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final DefaultRedisScript<List> tokenBucketScript;

    public DistributedRateLimiterService(
            StringRedisTemplate redisTemplate,
            ApiKeyRepository apiKeyRepository,
            RequestStatsService requestStatsService,
            MeterRegistry meterRegistry,
            @Value("${ratelimiter.redis-prefix:ratelimiter}") String redisPrefix,
            @Value("${ratelimiter.token-bucket.refill-per-second:1.0}") double tokenBucketRefillPerSecond,
            @Value("${ratelimiter.token-bucket.capacity-multiplier:1.0}") double tokenBucketCapacityMultiplier
    ) {
        this.redisTemplate = redisTemplate;
        this.apiKeyRepository = apiKeyRepository;
        this.requestStatsService = requestStatsService;
        this.meterRegistry = meterRegistry;
        this.redisPrefix = redisPrefix == null || redisPrefix.isBlank() ? "ratelimiter" : redisPrefix.trim();
        this.tokenBucketRefillPerSecond = tokenBucketRefillPerSecond;
        this.tokenBucketCapacityMultiplier = tokenBucketCapacityMultiplier;

        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setScriptText(FIXED_WINDOW_SCRIPT);
        this.fixedWindowScript.setResultType(List.class);

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(List.class);

        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(TOKEN_BUCKET_SCRIPT);
        this.tokenBucketScript.setResultType(List.class);
    }

    @Transactional
    public Decision evaluate(String rawApiKey, String route, int tokens) {
        return evaluate(rawApiKey, route, tokens, null);
    }

    @Transactional
    public Decision evaluate(String rawApiKey, String route, int tokens, String requestedAlgorithm) {
        String apiKeyValue = rawApiKey == null ? "" : rawApiKey.trim();
        if (apiKeyValue.isEmpty()) {
            throw new IllegalArgumentException("apiKey is required");
        }

        ApiKey apiKey = apiKeyRepository.findByApiKey(apiKeyValue)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        String algorithm = resolveAlgorithm(requestedAlgorithm, apiKey.getAlgorithm());

        int cost = Math.max(1, tokens);
        String routeKey = normalizeRoute(route);
        apiKey.setAlgorithm(algorithm);

        Decision decision;
        try {
            decision = switch (algorithm) {
                case FIXED_WINDOW -> fixedWindowDecision(apiKey, routeKey, cost);
                case TOKEN_BUCKET -> tokenBucketDecision(apiKey, routeKey, cost);
                default -> slidingWindowDecision(apiKey, routeKey, cost);
            };
        } catch (RedisSystemException ex) {
            throw new IllegalStateException("Rate limiter unavailable", ex);
        }

        updateStats(apiKey, decision.allowed());
        meterRegistry.counter(
                "ratelimiter_requests_total",
                "algorithm",
                algorithm,
                "route",
                routeKey,
                "result",
                decision.allowed() ? "allowed" : "blocked"
        ).increment();

        return new Decision(decision.allowed(), decision.retryAfterSeconds(), decision.reason(), algorithm);
    }

    private Decision fixedWindowDecision(ApiKey apiKey, String route, int cost) {
        long now = System.currentTimeMillis();
        long windowMs = windowMs(apiKey);
        long bucketStart = now - (now % windowMs);
        String windowKey = redisPrefix + ":fixed:" + apiKey.getApiKey() + ":" + route + ":" + bucketStart;
        List<Long> result = redisTemplate.execute(
                fixedWindowScript,
                List.of(windowKey),
                Long.toString(now),
                Long.toString(windowMs),
                Integer.toString(Math.max(1, apiKey.getRateLimit())),
                Integer.toString(cost)
        );
        return toDecision(result, "FIXED_WINDOW_EXCEEDED", FIXED_WINDOW);
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
        return toDecision(result, "SLIDING_WINDOW_EXCEEDED", SLIDING_WINDOW);
    }

    private Decision tokenBucketDecision(ApiKey apiKey, String route, int cost) {
        String bucketKey = redisPrefix + ":bucket:" + apiKey.getApiKey() + ":" + route;
        int limit = Math.max(1, apiKey.getRateLimit());
        int windowSeconds = Math.max(1, apiKey.getWindowSeconds() == null ? 60 : apiKey.getWindowSeconds());
        double computedRefillPerSecond = tokenBucketRefillPerSecond > 0
                ? tokenBucketRefillPerSecond
                : ((double) limit / (double) windowSeconds);
        double refillPerMs = Math.max(0.0001, computedRefillPerSecond / 1000.0);
        int capacity = Math.max(1, (int) Math.ceil(limit * Math.max(0.1, tokenBucketCapacityMultiplier)));
        List<Long> result = redisTemplate.execute(
                tokenBucketScript,
                List.of(bucketKey),
                Long.toString(System.currentTimeMillis()),
                Integer.toString(capacity),
                Double.toString(refillPerMs),
                Integer.toString(cost)
        );
        return toDecision(result, "TOKEN_BUCKET_EXCEEDED", TOKEN_BUCKET);
    }

    private Decision toDecision(List<Long> result, String defaultReason, String algorithm) {
        if (result == null || result.size() < 3) {
            return new Decision(false, 1, "LIMITER_ERROR", algorithm);
        }
        boolean allowed = result.get(0) != null && result.get(0) == 1L;
        int retryAfter = result.get(1) == null ? 1 : Math.max(1, result.get(1).intValue());
        long code = result.get(2) == null ? 4L : result.get(2);
        String reason = switch ((int) code) {
            case 0 -> "ALLOWED";
            case 1 -> "FIXED_WINDOW_EXCEEDED";
            case 2 -> "SLIDING_WINDOW_EXCEEDED";
            case 3 -> "TOKEN_BUCKET_EXCEEDED";
            default -> defaultReason;
        };
        return allowed
                ? new Decision(true, 0, reason, algorithm)
                : new Decision(false, retryAfter, reason, algorithm);
    }

    private void updateStats(ApiKey apiKey, boolean allowed) {
        long total = apiKey.getTotalRequests() == null ? 0L : apiKey.getTotalRequests();
        long allowedCount = apiKey.getAllowedRequests() == null ? 0L : apiKey.getAllowedRequests();
        long blockedCount = apiKey.getBlockedRequests() == null ? 0L : apiKey.getBlockedRequests();
        apiKey.setTotalRequests(total + 1L);
        if (allowed) {
            apiKey.setAllowedRequests(allowedCount + 1L);
        } else {
            apiKey.setBlockedRequests(blockedCount + 1L);
        }

        if (allowed) {
            clearBlockMarker(apiKey.getApiKey());
            apiKey.setStatus("Normal");
        } else {
            setBlockMarker(apiKey.getApiKey(), apiKey.getWindowSeconds());
            apiKey.setStatus("Blocked");
        }
        apiKeyRepository.save(apiKey);

        if (allowed) {
            requestStatsService.incrementAllowed(1L);
        } else {
            requestStatsService.incrementBlocked(1L);
        }
    }

    public String resolveCurrentStatus(ApiKey apiKey) {
        if (apiKey == null || apiKey.getApiKey() == null || apiKey.getApiKey().isBlank()) {
            return "Normal";
        }
        return hasActiveBlockMarker(apiKey.getApiKey()) ? "Blocked" : "Normal";
    }

    public long resolveCurrentWindowRequestCount(ApiKey apiKey) {
        if (apiKey == null || apiKey.getApiKey() == null || apiKey.getApiKey().isBlank()) {
            return 0L;
        }

        String apiKeyValue = apiKey.getApiKey();
        long sliding = sumSlidingWindowCount(apiKeyValue);
        long fixed = sumFixedWindowCount(apiKeyValue);
        long bucket = sumTokenBucketUsed(apiKeyValue, apiKey.getRateLimit(), apiKey.getWindowSeconds());
        return Math.max(0L, sliding + fixed + bucket);
    }

    private boolean hasActiveBlockMarker(String apiKeyValue) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockMarkerKey(apiKeyValue)));
        } catch (Exception ignored) {
            // Fallback to non-blocking status if Redis is temporarily unavailable.
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

    private long sumSlidingWindowCount(String apiKeyValue) {
        Set<String> keys = redisTemplate.keys(redisPrefix + ":sliding:" + apiKeyValue + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (String key : keys) {
            Long value = redisTemplate.opsForZSet().zCard(key);
            sum += value == null ? 0L : value;
        }
        return sum;
    }

    private long sumFixedWindowCount(String apiKeyValue) {
        Set<String> keys = redisTemplate.keys(redisPrefix + ":fixed:" + apiKeyValue + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                sum += Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // Ignore malformed values.
            }
        }
        return sum;
    }

    private long sumTokenBucketUsed(String apiKeyValue, Integer rateLimit, Integer windowSeconds) {
        Set<String> keys = redisTemplate.keys(redisPrefix + ":bucket:" + apiKeyValue + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }

        int limit = Math.max(1, rateLimit == null ? 1 : rateLimit);
        int window = Math.max(1, windowSeconds == null ? 60 : windowSeconds);
        double refillPerSecond = tokenBucketRefillPerSecond > 0
                ? tokenBucketRefillPerSecond
                : ((double) limit / (double) window);
        double capacity = Math.max(1.0, Math.ceil(limit * Math.max(0.1, tokenBucketCapacityMultiplier)));
        if (refillPerSecond <= 0) {
            return 0L;
        }

        long used = 0L;
        for (String key : keys) {
            Object tokensRaw = redisTemplate.opsForHash().get(key, "tokens");
            if (tokensRaw == null) {
                continue;
            }
            try {
                double tokens = Double.parseDouble(tokensRaw.toString());
                used += Math.max(0L, (long) Math.ceil(capacity - tokens));
            } catch (NumberFormatException ignored) {
                // Ignore malformed values.
            }
        }
        return used;
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

    private String normalizeAlgorithm(String algorithm) {
        if (algorithm == null) {
            return "";
        }
        return algorithm.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveAlgorithm(String requestedAlgorithm, String configuredAlgorithm) {
        String requested = normalizeAlgorithm(requestedAlgorithm);
        if (!requested.isBlank()) {
            if (isSupportedAlgorithm(requested)) {
                return requested;
            }
            throw new IllegalArgumentException(
                    "Algorithm must be SLIDING_WINDOW"
            );
        }

        String configured = normalizeAlgorithm(configuredAlgorithm);
        if (isSupportedAlgorithm(configured)) {
            return configured;
        }
        return SLIDING_WINDOW;
    }

    private boolean isSupportedAlgorithm(String algorithm) {
        return SLIDING_WINDOW.equals(algorithm);
    }

    public record Decision(boolean allowed, int retryAfterSeconds, String reason, String algorithm) {}
}
