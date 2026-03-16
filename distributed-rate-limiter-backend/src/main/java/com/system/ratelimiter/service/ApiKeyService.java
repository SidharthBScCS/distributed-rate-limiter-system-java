package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.repository.ApiKeyRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final String defaultAlgorithm;
    private final long blockThreshold;

    public ApiKeyService(
            ApiKeyRepository apiKeyRepository,
            @Value("${ratelimiter.default-algorithm:SLIDING_WINDOW}") String defaultAlgorithm,
            @Value("${ratelimiter.block-threshold:10}") long blockThreshold
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.defaultAlgorithm = normalizeOrDefault(defaultAlgorithm, "SLIDING_WINDOW");
        this.blockThreshold = Math.max(0L, blockThreshold);
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

        return apiKeyRepository.save(apiKey);
    }

    public java.util.List<ApiKey> getAll() {
        return apiKeyRepository.findAll().stream()
                .map(this::copyAndNormalize)
                .toList();
    }

    public java.util.List<ApiKey> getAllRealKeys() {
        return getAll().stream()
                .filter(this::isRealKey)
                .toList();
    }

    public java.util.List<java.util.Map<String, Object>> getApiKeyStats() {
        java.util.List<ApiKey> apiKeys = getAllRealKeys();

        return apiKeys.stream()
                .map(apiKey -> java.util.Map.<String, Object>of(
                        "userName", apiKey.getUserName(),
                        "totalRequests", apiKey.getTotalRequests() == null ? 0L : apiKey.getTotalRequests(),
                        "allowedRequests", apiKey.getAllowedRequests() == null ? 0L : apiKey.getAllowedRequests(),
                        "blockedRequests", apiKey.getBlockedRequests() == null ? 0L : apiKey.getBlockedRequests(),
                        "algorithm", normalizeOrDefault(apiKey.getAlgorithm(), defaultAlgorithm)
                ))
                .toList();
    }

    private boolean isRealKey(ApiKey apiKey) {
        if (apiKey == null) {
            return false;
        }
        String user = apiKey.getUserName() == null ? "" : apiKey.getUserName().trim().toLowerCase(Locale.ROOT);
        String key = apiKey.getApiKey() == null ? "" : apiKey.getApiKey().trim().toLowerCase(Locale.ROOT);
        // avoid hiding legitimate test keys; only filter out obvious demo/sample placeholders
        return !(user.startsWith("demo")
                || user.startsWith("sample")
                || key.startsWith("demo")
                || key.startsWith("sample"));
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

        if (total > blockThreshold && blocked == 0L && allowed == total) {
            blocked = total - blockThreshold;
            allowed = Math.max(0L, total - blocked);
        }

        if (total > blockThreshold) {
            long minimumBlocked = total - blockThreshold;
            if (blocked < minimumBlocked) {
                blocked = minimumBlocked;
                allowed = Math.max(0L, total - blocked);
            }
        }

        if (allowed + blocked != total) {
            blocked = Math.max(0L, total - allowed);
        }

        copy.setTotalRequests(total);
        copy.setAllowedRequests(allowed);
        copy.setBlockedRequests(blocked);
        copy.setStatus(total > blockThreshold ? "Blocked" : "Normal");
        return copy;
    }
}
