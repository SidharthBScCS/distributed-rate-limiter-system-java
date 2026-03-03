package com.system.ratelimiter.controller;
import com.system.ratelimiter.dto.RateLimitCheckRequest;
import com.system.ratelimiter.dto.CreateApiKeyRequest;
import com.system.ratelimiter.dto.RateLimitDecisionResponse;
import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.entity.RequestStats;
import com.system.ratelimiter.service.ApiKeyService;
import com.system.ratelimiter.service.DistributedRateLimiterService;
import com.system.ratelimiter.service.RequestStatsService;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
@RequestMapping("/api")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final RequestStatsService requestStatsService;
    private final DistributedRateLimiterService distributedRateLimiterService;

    public ApiKeyController(
            ApiKeyService apiKeyService,
            RequestStatsService requestStatsService,
            DistributedRateLimiterService distributedRateLimiterService
    ) {
        this.apiKeyService = apiKeyService;
        this.requestStatsService = requestStatsService;
        this.distributedRateLimiterService = distributedRateLimiterService;
    }

    @GetMapping
    public ResponseEntity<List<ApiKey>> listApiKeys() {
        return ResponseEntity.ok(apiKeyService.getAllRealKeys());
    }

    @GetMapping("/stats")
    public ResponseEntity<RequestStats> getStats() {
        return ResponseEntity.ok(requestStatsService.getOrCreate());
    }

    @GetMapping("/analytics/keys")
    public ResponseEntity<List<Map<String, Object>>> getApiKeyStats() {
        return ResponseEntity.ok(apiKeyService.getApiKeyStats());
    }

    @GetMapping("/view/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardView() {
        List<ApiKey> allKeys = apiKeyService.getAllRealKeys();
        long total = allKeys.stream().mapToLong(k -> k.getTotalRequests() == null ? 0L : k.getTotalRequests()).sum();
        long allowed = allKeys.stream().mapToLong(k -> k.getAllowedRequests() == null ? 0L : k.getAllowedRequests()).sum();
        long blocked = allKeys.stream().mapToLong(k -> k.getBlockedRequests() == null ? 0L : k.getBlockedRequests()).sum();

        double allowedPercent = total == 0 ? 0.0 : (allowed * 100.0) / total;
        double blockedPercent = total == 0 ? 0.0 : (blocked * 100.0) / total;

        List<Map<String, Object>> apiKeys = allKeys.stream()
                .map(apiKey -> {
                    long requestCount = apiKey.getTotalRequests() == null ? 0L : apiKey.getTotalRequests();
                    int rateLimit = apiKey.getRateLimit() == null || apiKey.getRateLimit() <= 0 ? 1 : apiKey.getRateLimit();
                    double usagePercentage = Math.min((requestCount * 100.0) / rateLimit, 100.0);
                    String status = apiKey.getStatus() == null ? "Normal" : apiKey.getStatus();
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", apiKey.getId());
                    row.put("apiKey", apiKey.getApiKey());
                    row.put("userName", apiKey.getUserName());
                    row.put("rateLimit", apiKey.getRateLimit());
                    row.put("windowSeconds", apiKey.getWindowSeconds());
                    row.put("algorithm", apiKey.getAlgorithm());
                    row.put("requestCount", requestCount);
                    row.put("usagePercentage", usagePercentage);
                    row.put("usageColor", usageColor(usagePercentage));
                    row.put("status", status);
                    row.put("statusColor", statusColor(status));
                    return row;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "stats", Map.of(
                        "totalRequests", total,
                        "allowedRequests", allowed,
                        "blockedRequests", blocked,
                        "allowedPercent", allowedPercent,
                        "blockedPercent", blockedPercent
                ),
                "apiKeys", apiKeys
        ));
    }

    @PostMapping("/limit/check")
    public ResponseEntity<RateLimitDecisionResponse> checkLimit(@Valid @RequestBody RateLimitCheckRequest request) {
        DistributedRateLimiterService.Decision decision = distributedRateLimiterService.evaluate(
                request.getApiKey(),
                request.getRoute(),
                request.getTokens() == null ? 1 : request.getTokens(),
                request.getAlgorithm()
        );
        RateLimitDecisionResponse body = new RateLimitDecisionResponse(
                decision.allowed(),
                decision.retryAfterSeconds(),
                decision.reason(),
                decision.algorithm(),
                request.getApiKey()
        );
        if (!decision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/keys")
    public ResponseEntity<ApiKey> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        ApiKey created = apiKeyService.createApiKey(
                request.getUserName(),
                request.getRateLimit(),
                request.getWindowSeconds(),
                request.getAlgorithm()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Invalid request.";
        FieldError error = ex.getBindingResult().getFieldError();
        if (error != null && error.getDefaultMessage() != null) {
            message = error.getDefaultMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Invalid request." : ex.getMessage();
        HttpStatus status = "API key not found".equalsIgnoreCase(message)
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "Service unavailable." : ex.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("message", message));
    }

    @GetMapping("/analytics/view")
    public ResponseEntity<Map<String, Object>> getAnalyticsView() {
        List<Map<String, Object>> raw = apiKeyService.getApiKeyStats();
        List<Map<String, Object>> top = raw.stream()
                .sorted(Comparator.comparingLong(item -> -1L * ((Number) item.getOrDefault("totalRequests", 0)).longValue()))
                .limit(7)
                .toList();

        List<String> labels = top.stream()
                .map(item -> String.valueOf(item.getOrDefault("userName", "Unknown")))
                .toList();

        List<Long> totals = top.stream()
                .map(item -> ((Number) item.getOrDefault("totalRequests", 0)).longValue())
                .toList();

        List<Long> allowed = top.stream()
                .map(item -> ((Number) item.getOrDefault("allowedRequests", 0)).longValue())
                .toList();

        List<Long> blocked = top.stream()
                .map(item -> ((Number) item.getOrDefault("blockedRequests", 0)).longValue())
                .toList();

        long sumTotal = totals.stream().mapToLong(Long::longValue).sum();
        long sumAllowed = allowed.stream().mapToLong(Long::longValue).sum();
        long sumBlocked = blocked.stream().mapToLong(Long::longValue).sum();

        long maxValue = 1;
        for (Long value : totals) {
            if (value > maxValue) maxValue = value;
        }
        for (Long value : allowed) {
            if (value > maxValue) maxValue = value;
        }
        for (Long value : blocked) {
            if (value > maxValue) maxValue = value;
        }

        return ResponseEntity.ok(Map.of(
                "labels", labels,
                "totalRequests", totals,
                "successRequests", allowed,
                "blockedRequests", blocked,
                "summary", Map.of(
                        "total", sumTotal,
                        "success", sumAllowed,
                        "blocked", sumBlocked
                ),
                "maxValue", maxValue
        ));
    }

    private static String statusColor(String status) {
        String value = status == null ? "" : status.toLowerCase();
        if ("blocked".equals(value)) return "#ef4444";
        if ("warning".equals(value)) return "#f59e0b";
        if ("normal".equals(value)) return "#10b981";
        return "#94a3b8";
    }

    private static String usageColor(double percentage) {
        if (percentage > 90) return "#ef4444";
        if (percentage > 70) return "#f59e0b";
        return "#10b981";
    }
}
