package com.system.ratelimiter.controller;
import com.system.ratelimiter.dto.RateLimitCheckRequest;
import com.system.ratelimiter.dto.CreateApiKeyRequest;
import com.system.ratelimiter.dto.DecisionAuditEntry;
import com.system.ratelimiter.dto.RateLimitDecisionResponse;
import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.entity.RequestStats;
import com.system.ratelimiter.service.ApiKeyService;
import com.system.ratelimiter.service.DecisionAuditService;
import com.system.ratelimiter.service.DistributedRateLimiterService;
import com.system.ratelimiter.service.RequestStatsService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@RequestMapping("/api")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final RequestStatsService requestStatsService;
    private final DistributedRateLimiterService distributedRateLimiterService;
    private final DecisionAuditService decisionAuditService;
    private final long dashboardCacheTtlMs;
    private final ConcurrentHashMap<String, CachedDashboardView> dashboardCache = new ConcurrentHashMap<>();

    public ApiKeyController(
            ApiKeyService apiKeyService,
            RequestStatsService requestStatsService,
            DistributedRateLimiterService distributedRateLimiterService,
            DecisionAuditService decisionAuditService,
            @Value("${ui.dashboard-cache-ttl-ms:500}") long dashboardCacheTtlMs
    ) {
        this.apiKeyService = apiKeyService;
        this.requestStatsService = requestStatsService;
        this.distributedRateLimiterService = distributedRateLimiterService;
        this.decisionAuditService = decisionAuditService;
        this.dashboardCacheTtlMs = Math.max(100L, dashboardCacheTtlMs);
    }

    @GetMapping
    public ResponseEntity<List<ApiKey>> listApiKeys() {
        return ResponseEntity.ok(apiKeyService.getAllRealKeys());
    }

    @GetMapping("/stats")
    public ResponseEntity<RequestStats> getStats() {
        return ResponseEntity.ok(requestStatsService.snapshot());
    }

    @GetMapping("/analytics/keys")
    public ResponseEntity<List<Map<String, Object>>> getApiKeyStats() {
        return ResponseEntity.ok(apiKeyService.getApiKeyStats());
    }

    @GetMapping("/view/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardView(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size
    ) {
        String normalizedSearch = normalizeSearch(search);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        String cacheKey = dashboardCacheKey(normalizedSearch, safePage, safeSize);
        long now = System.currentTimeMillis();
        CachedDashboardView cached = dashboardCache.get(cacheKey);
        if (cached != null && now - cached.createdAtMs() < dashboardCacheTtlMs) {
            return ResponseEntity.ok(cached.payload());
        }

        Map<String, Object> response = buildDashboardView(normalizedSearch, safePage, safeSize);
        dashboardCache.entrySet().removeIf(entry -> now - entry.getValue().createdAtMs() >= dashboardCacheTtlMs);
        dashboardCache.put(cacheKey, new CachedDashboardView(response, now));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildDashboardView(String search, int page, int size) {
        RequestStats statsSnapshot = requestStatsService.snapshot();
        List<ApiKey> allKeys = apiKeyService.getAllRealKeys();
        long total = statsSnapshot.getTotalRequests() == null ? 0L : statsSnapshot.getTotalRequests();
        long allowed = statsSnapshot.getAllowedRequests() == null ? 0L : statsSnapshot.getAllowedRequests();
        long blocked = statsSnapshot.getBlockedRequests() == null ? 0L : statsSnapshot.getBlockedRequests();

        double allowedPercent = total == 0 ? 0.0 : (allowed * 100.0) / total;
        double blockedPercent = total == 0 ? 0.0 : (blocked * 100.0) / total;

        List<Map<String, Object>> apiKeys = allKeys.stream()
                .map(apiKey -> {
                    long requestCount = distributedRateLimiterService.resolveCurrentWindowRequestCount(apiKey);
                    int rateLimit = apiKey.getRateLimit() == null || apiKey.getRateLimit() <= 0 ? 1 : apiKey.getRateLimit();
                    double usagePercentage = Math.min((requestCount * 100.0) / rateLimit, 100.0);
                    String status = distributedRateLimiterService.resolveCurrentStatus(apiKey);
                    Map<String, Object> row = new LinkedHashMap<>();
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
                .filter(row -> matchesDashboardSearch(row, search))
                .toList();
        apiKeys = mergeSortApiKeysByUsage(apiKeys);
        int totalApiKeys = apiKeys.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalApiKeys / (double) size));
        int safePage = Math.min(Math.max(1, page), totalPages);
        int startIndex = Math.min((safePage - 1) * size, totalApiKeys);
        int endIndex = Math.min(startIndex + size, totalApiKeys);
        List<Map<String, Object>> pageRows = apiKeys.subList(startIndex, endIndex);

        return Map.of(
                "stats", Map.of(
                        "totalRequests", total,
                        "allowedRequests", allowed,
                        "blockedRequests", blocked,
                        "totalPercent", total == 0 ? 0.0 : 100.0,
                        "allowedPercent", allowedPercent,
                        "blockedPercent", blockedPercent
                ),
                "apiKeys", pageRows,
                "pagination", Map.of(
                        "page", safePage,
                        "size", size,
                        "totalItems", totalApiKeys,
                        "totalPages", totalPages,
                        "filtered", !search.isEmpty(),
                        "search", search
                ),
                "sources", Map.of(
                        "postgres", "request_stats totals, api_keys metadata and persisted counters",
                        "redis", "live window counters and block markers"
                ),
                "generatedAt", java.time.Instant.now().toString()
        );
    }

    @PostMapping("/limit/check")
    public ResponseEntity<RateLimitDecisionResponse> checkLimit(@Valid @RequestBody RateLimitCheckRequest request) {
        DistributedRateLimiterService.Decision decision = distributedRateLimiterService.evaluate(
                request.getApiKey(),
                request.getRoute(),
                request.getTokens() == null ? 1 : request.getTokens()
        );
        String safeReason = normalizeReason(decision.allowed(), decision.reason());
        RateLimitDecisionResponse body = new RateLimitDecisionResponse(
                decision.allowed(),
                decision.retryAfterSeconds(),
                safeReason,
                decision.algorithm(),
                request.getApiKey(),
                decision.route(),
                decision.limit(),
                decision.windowSeconds(),
                decision.currentUsage(),
                decision.remainingRequests(),
                decision.evaluatedAt().toString()
        );
        if (!decision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/analytics/recent-decisions")
    public ResponseEntity<List<DecisionAuditEntry>> getRecentDecisions() {
        return ResponseEntity.ok(decisionAuditService.getRecent(50));
    }

    private static String normalizeReason(boolean allowed, String reason) {
        String value = reason == null ? "" : reason.trim();
        if (allowed) {
            return value.isEmpty() ? "ALLOWED" : value;
        }
        if (value.isEmpty() || "ALLOWED".equalsIgnoreCase(value)) {
            return "RATE_LIMIT_EXCEEDED";
        }
        return value;
    }

    @PostMapping("/keys")
    public ResponseEntity<ApiKey> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        int rateLimit = parsePositiveInt(request.getRateLimit(), "rateLimit");
        int windowSeconds = parsePositiveInt(request.getWindowSeconds(), "windowSeconds");
        ApiKey created = apiKeyService.createApiKey(
                request.getUserName(),
                rateLimit,
                windowSeconds
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private static int parsePositiveInt(String value, String field) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a valid integer");
        }
        if (parsed < 1) {
            throw new IllegalArgumentException(field + " must be >= 1");
        }
        return parsed;
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

    private static String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase();
    }

    private static boolean matchesDashboardSearch(Map<String, Object> row, String search) {
        if (search == null || search.isEmpty()) {
            return true;
        }
        return List.of(
                        row.get("apiKey"),
                        row.get("userName"),
                        row.get("status"),
                        row.get("algorithm"),
                        row.get("rateLimit"),
                        row.get("windowSeconds")
                ).stream()
                .filter(value -> value != null)
                .map(String::valueOf)
                .map(String::toLowerCase)
                .anyMatch(value -> value.contains(search));
    }

    private static String dashboardCacheKey(String search, int page, int size) {
        return search + "|" + page + "|" + size;
    }

    private static List<Map<String, Object>> mergeSortApiKeysByUsage(List<Map<String, Object>> apiKeys) {
        if (apiKeys == null || apiKeys.size() <= 1) {
            return apiKeys == null ? List.of() : apiKeys;
        }

        int midpoint = apiKeys.size() / 2;
        List<Map<String, Object>> left = mergeSortApiKeysByUsage(new ArrayList<>(apiKeys.subList(0, midpoint)));
        List<Map<String, Object>> right = mergeSortApiKeysByUsage(new ArrayList<>(apiKeys.subList(midpoint, apiKeys.size())));

        return mergeApiKeyLists(left, right);
    }

    private static List<Map<String, Object>> mergeApiKeyLists(
            List<Map<String, Object>> left,
            List<Map<String, Object>> right
    ) {
        List<Map<String, Object>> merged = new ArrayList<>(left.size() + right.size());
        int leftIndex = 0;
        int rightIndex = 0;

        while (leftIndex < left.size() && rightIndex < right.size()) {
            Map<String, Object> leftRow = left.get(leftIndex);
            Map<String, Object> rightRow = right.get(rightIndex);

            if (compareApiKeyRows(leftRow, rightRow) <= 0) {
                merged.add(leftRow);
                leftIndex++;
            } else {
                merged.add(rightRow);
                rightIndex++;
            }
        }

        while (leftIndex < left.size()) {
            merged.add(left.get(leftIndex++));
        }

        while (rightIndex < right.size()) {
            merged.add(right.get(rightIndex++));
        }

        return merged;
    }

    private static int compareApiKeyRows(Map<String, Object> leftRow, Map<String, Object> rightRow) {
        int usageComparison = Double.compare(
                toDouble(rightRow.get("usagePercentage")),
                toDouble(leftRow.get("usagePercentage"))
        );
        if (usageComparison != 0) {
            return usageComparison;
        }

        int requestCountComparison = Long.compare(
                toLong(rightRow.get("requestCount")),
                toLong(leftRow.get("requestCount"))
        );
        if (requestCountComparison != 0) {
            return requestCountComparison;
        }

        return String.CASE_INSENSITIVE_ORDER.compare(
                String.valueOf(leftRow.getOrDefault("userName", "")),
                String.valueOf(rightRow.getOrDefault("userName", ""))
        );
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record CachedDashboardView(Map<String, Object> payload, long createdAtMs) {}
}
