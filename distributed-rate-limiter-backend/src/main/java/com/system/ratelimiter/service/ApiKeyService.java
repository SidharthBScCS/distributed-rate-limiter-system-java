package com.system.ratelimiter.service;

import com.system.ratelimiter.dto.ApiKeyStatsItemDto;
import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.repository.ApiKeyRepository;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final String defaultAlgorithm;
    private final ConcurrentHashMap<String, ApiKey> apiKeyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ApiKeyDelta> pendingCounterDeltas = new ConcurrentHashMap<>();

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            @Value("${ratelimiter.default-algorithm:SLIDING_WINDOW}") String defaultAlgorithm
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.defaultAlgorithm = normalizeOrDefault(defaultAlgorithm, "SLIDING_WINDOW");
    }

    @PostConstruct
    public void warmCache() {
        refreshCache();
    }

    public ApiKey createApiKey(String userName, Integer rateLimit, Integer windowSeconds) {
        if (userName == null || userName.trim().isEmpty()) throw new IllegalArgumentException("userName is required");
        if (rateLimit == null || rateLimit <= 0) throw new IllegalArgumentException("rateLimit must be > 0");
        if (windowSeconds == null || windowSeconds <= 0) throw new IllegalArgumentException("windowSeconds must be > 0");

        ApiKey apiKey = new ApiKey();
        apiKey.setUserName(userName.trim());
        apiKey.setRateLimit(rateLimit);
        apiKey.setWindowSeconds(windowSeconds);
        apiKey.setAlgorithm(defaultAlgorithm);
        // generate a random API key token
        String token = UUID.randomUUID().toString().replace("-", "");
        apiKey.setApiKey(token);
        apiKey.setStatus("Normal");
        apiKey.setTotalRequests(0L);
        apiKey.setAllowedRequests(0L);
        apiKey.setBlockedRequests(0L);

        ApiKey saved = apiKeyRepository.save(apiKey);
        apiKeyCache.put(saved.getApiKey(), copyAndNormalize(saved));
        return saved;
    }

    public java.util.List<ApiKey> getAll() {
        return apiKeyRepository.findAll().stream()
                .map(this::copyAndNormalize)
                .toList();
    }

    public java.util.List<ApiKey> getAllRealKeys() {
        return apiKeyRepository.findAllRealKeys().stream()
                .map(this::copyAndNormalize)
                .toList();
    }

    public Page<ApiKey> getDashboardKeys(String search, int page, int size) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        return apiKeyRepository.findDashboardKeys(
                normalized,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "totalRequests"))
        ).map(this::copyAndNormalize);
    }

    public java.util.List<ApiKey> getAllDashboardKeys(String search) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return apiKeyRepository.findAllDashboardKeys(normalized).stream()
                .map(this::copyAndNormalize)
                .toList();
    }

    public java.util.List<ApiKey> getTopKeys(int limit) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        return apiKeyRepository.findTopKeys(PageRequest.of(0, safeLimit))
                .map(this::copyAndNormalize)
                .getContent();
    }

    public ApiKey getCachedByValue(String apiKeyValue) {
        if (apiKeyValue == null || apiKeyValue.isBlank()) {
            return null;
        }
        ApiKey cached = apiKeyCache.get(apiKeyValue.trim());
        if (cached != null) {
            return copyAndNormalize(cached);
        }
        return apiKeyRepository.findByApiKey(apiKeyValue.trim())
                .map(this::copyAndNormalize)
                .map(apiKey -> {
                    apiKeyCache.put(apiKey.getApiKey(), apiKey);
                    return copyAndNormalize(apiKey);
                })
                .orElse(null);
    }

    public void recordDecision(String apiKeyValue, boolean allowed) {
        if (apiKeyValue == null || apiKeyValue.isBlank()) {
            return;
        }
        ApiKeyDelta delta = pendingCounterDeltas.computeIfAbsent(apiKeyValue.trim(), ignored -> new ApiKeyDelta());
        delta.total.incrementAndGet();
        if (allowed) {
            delta.allowed.incrementAndGet();
            delta.status.set("Normal");
        } else {
            delta.blocked.incrementAndGet();
            delta.status.set("Blocked");
        }
    }

    @Scheduled(fixedDelayString = "${ratelimiter.api-key-cache-refresh-ms:30000}")
    public void refreshCache() {
        Map<String, ApiKey> latest = new ConcurrentHashMap<>();
        for (ApiKey apiKey : apiKeyRepository.findAll()) {
            ApiKey normalized = copyAndNormalize(apiKey);
            latest.put(normalized.getApiKey(), normalized);
        }
        apiKeyCache.clear();
        apiKeyCache.putAll(latest);
    }

    @Scheduled(fixedDelayString = "${ratelimiter.stats.flush-ms:5000}")
    @Transactional
    public void flushPendingCounters() {
        if (pendingCounterDeltas.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ApiKeyDelta> entry : pendingCounterDeltas.entrySet()) {
            String apiKeyValue = entry.getKey();
            ApiKeyDelta delta = entry.getValue();
            long totalDelta = delta.total.getAndSet(0L);
            long allowedDelta = delta.allowed.getAndSet(0L);
            long blockedDelta = delta.blocked.getAndSet(0L);
            String status = delta.status.get();

            if (totalDelta == 0L && allowedDelta == 0L && blockedDelta == 0L) {
                pendingCounterDeltas.remove(apiKeyValue, delta);
                continue;
            }

            apiKeyRepository.incrementCountersAndStatus(apiKeyValue, totalDelta, allowedDelta, blockedDelta, status);
            apiKeyCache.computeIfPresent(apiKeyValue, (ignored, current) -> applyDelta(current, totalDelta, allowedDelta, blockedDelta, status));

            if (delta.total.get() == 0L && delta.allowed.get() == 0L && delta.blocked.get() == 0L) {
                pendingCounterDeltas.remove(apiKeyValue, delta);
            }
        }
    }

    public java.util.List<ApiKeyStatsItemDto> getApiKeyStats() {
        java.util.List<ApiKey> apiKeys = getTopKeys(50);

        return apiKeys.stream()
                .map(apiKey -> new ApiKeyStatsItemDto(
                        apiKey.getUserName(),
                        apiKey.getTotalRequests() == null ? 0L : apiKey.getTotalRequests(),
                        apiKey.getAllowedRequests() == null ? 0L : apiKey.getAllowedRequests(),
                        apiKey.getBlockedRequests() == null ? 0L : apiKey.getBlockedRequests(),
                        normalizeOrDefault(apiKey.getAlgorithm(), defaultAlgorithm)
                ))
                .toList();
    }

    private ApiKey applyDelta(ApiKey source, long totalDelta, long allowedDelta, long blockedDelta, String status) {
        ApiKey updated = copyAndNormalize(source);
        updated.setTotalRequests((updated.getTotalRequests() == null ? 0L : updated.getTotalRequests()) + totalDelta);
        updated.setAllowedRequests((updated.getAllowedRequests() == null ? 0L : updated.getAllowedRequests()) + allowedDelta);
        updated.setBlockedRequests((updated.getBlockedRequests() == null ? 0L : updated.getBlockedRequests()) + blockedDelta);
        if (status != null && !status.isBlank()) {
            updated.setStatus(status);
        }
        return updated;
    }

    private String normalizeOrDefault(String algorithm, String fallback) {
        String value = algorithm == null ? "" : algorithm.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return fallback;
        }
        return isSupportedAlgorithm(value) ? value : fallback;
    }

    private boolean isSupportedAlgorithm(String algorithm) {
        return "SLIDING_WINDOW".equals(algorithm);
    }

    private ApiKey copyAndNormalize(ApiKey source) {
        ApiKey copy = new ApiKey();
        copy.setId(source.getId());
        copy.setUserName(source.getUserName());
        copy.setRateLimit(source.getRateLimit());
        copy.setWindowSeconds(source.getWindowSeconds());
        copy.setAlgorithm(normalizeOrDefault(source.getAlgorithm(), defaultAlgorithm));
        copy.setApiKey(source.getApiKey());
        copy.setCreatedAt(source.getCreatedAt());

        long total = source.getTotalRequests() == null ? 0L : source.getTotalRequests();
        long allowed = source.getAllowedRequests() == null ? 0L : source.getAllowedRequests();
        long blocked = source.getBlockedRequests() == null ? 0L : source.getBlockedRequests();

        if (allowed + blocked != total) {
            blocked = Math.max(0L, blocked);
            allowed = Math.max(0L, total - blocked);
        }

        copy.setTotalRequests(total);
        copy.setAllowedRequests(allowed);
        copy.setBlockedRequests(blocked);
        copy.setStatus(source.getStatus() == null || source.getStatus().isBlank() ? "Normal" : source.getStatus());
        return copy;
    }

    private static final class ApiKeyDelta {
        private final AtomicLong total = new AtomicLong();
        private final AtomicLong allowed = new AtomicLong();
        private final AtomicLong blocked = new AtomicLong();
        private final AtomicReference<String> status = new AtomicReference<>("Normal");
    }
}
