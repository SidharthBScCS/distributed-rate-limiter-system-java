package com.system.ratelimiter.controller;
import com.system.ratelimiter.dto.AnalyticsSummaryDto;
import com.system.ratelimiter.dto.AnalyticsViewResponse;
import com.system.ratelimiter.dto.ApiErrorResponse;
import com.system.ratelimiter.dto.ApiKeyStatsItemDto;
import com.system.ratelimiter.dto.DashboardApiKeyRowDto;
import com.system.ratelimiter.dto.DashboardPaginationDto;
import com.system.ratelimiter.dto.DashboardSourcesDto;
import com.system.ratelimiter.dto.DashboardStatCardDto;
import com.system.ratelimiter.dto.DashboardStatsDto;
import com.system.ratelimiter.dto.DashboardViewResponse;
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
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
    public ResponseEntity<List<ApiKeyStatsItemDto>> getApiKeyStats() {
        return ResponseEntity.ok(apiKeyService.getApiKeyStats());
    }

    @GetMapping("/view/dashboard")
    public ResponseEntity<DashboardViewResponse> getDashboardView(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "size", required = false, defaultValue = "15") int size
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

        DashboardViewResponse response = buildDashboardView(normalizedSearch, safePage, safeSize);
        dashboardCache.entrySet().removeIf(entry -> now - entry.getValue().createdAtMs() >= dashboardCacheTtlMs);
        dashboardCache.put(cacheKey, new CachedDashboardView(response, now));
        return ResponseEntity.ok(response);
    }

    private DashboardViewResponse buildDashboardView(String search, int page, int size) {
        RequestStats statsSnapshot = requestStatsService.snapshot();
        Page<ApiKey> apiKeyPage = apiKeyService.getDashboardKeys(search, page - 1, size);
        List<ApiKey> pageKeys = apiKeyPage.getContent();
        var liveSnapshots = distributedRateLimiterService.snapshotDashboardState(pageKeys);
        long total = statsSnapshot.getTotalRequests() == null ? 0L : statsSnapshot.getTotalRequests();
        long allowed = statsSnapshot.getAllowedRequests() == null ? 0L : statsSnapshot.getAllowedRequests();
        long blocked = statsSnapshot.getBlockedRequests() == null ? 0L : statsSnapshot.getBlockedRequests();

        double allowedPercent = total == 0 ? 0.0 : (allowed * 100.0) / total;
        double blockedPercent = total == 0 ? 0.0 : (blocked * 100.0) / total;
        List<DashboardStatCardDto> statCards = List.of(
                statCard("Total Requests", total, "All requests processed", formatPercent(total == 0 ? 0.0 : 100.0), total > 0 ? "up" : "down", "#94a3b8", "activity"),
                statCard("Allowed", allowed, "Passed rate limits", formatPercent(allowedPercent), allowedPercent > 0 ? "up" : "down", "#4ade80", "check-circle"),
                statCard("Blocked", blocked, "Throttled requests", formatPercent(blockedPercent), blockedPercent > 0 ? "down" : "up", "#f87171", "x-circle")
        );

        List<DashboardApiKeyRowDto> apiKeys = pageKeys.stream()
                .map(apiKey -> {
                    var liveSnapshot = liveSnapshots.getOrDefault(
                            apiKey.getApiKey(),
                            new DistributedRateLimiterService.DashboardLiveSnapshot("Normal", 0L)
                    );
                    long requestCount = liveSnapshot.requestCount();
                    int rateLimit = apiKey.getRateLimit() == null || apiKey.getRateLimit() <= 0 ? 1 : apiKey.getRateLimit();
                    double usagePercentage = Math.min((requestCount * 100.0) / rateLimit, 100.0);
                    String status = liveSnapshot.status();
                    return new DashboardApiKeyRowDto(
                            apiKey.getId(),
                            apiKey.getApiKey(),
                            apiKey.getUserName(),
                            apiKey.getRateLimit(),
                            apiKey.getWindowSeconds(),
                            apiKey.getAlgorithm(),
                            requestCount,
                            usagePercentage,
                            NumberFormat.getIntegerInstance().format(requestCount) + " req",
                            rateLimit + "/window",
                            (apiKey.getWindowSeconds() == null ? 0 : apiKey.getWindowSeconds()) + "s",
                            apiKey.getAlgorithm() == null || apiKey.getAlgorithm().isBlank() ? "-" : apiKey.getAlgorithm(),
                            formatUsagePercentage(usagePercentage),
                            usageColor(usagePercentage),
                            status,
                            status == null || status.isBlank() ? "Unknown" : status,
                            statusColor(status)
                    );
                })
                .toList();
        int totalApiKeys = Math.toIntExact(apiKeyPage.getTotalElements());
        int totalPages = Math.max(1, apiKeyPage.getTotalPages());
        int safePage = apiKeyPage.getNumberOfElements() == 0 ? 1 : apiKeyPage.getNumber() + 1;

        return new DashboardViewResponse(
                new DashboardStatsDto(
                        total,
                        NumberFormat.getIntegerInstance().format(total),
                        allowed,
                        NumberFormat.getIntegerInstance().format(allowed),
                        blocked,
                        NumberFormat.getIntegerInstance().format(blocked),
                        total == 0 ? 0.0 : 100.0,
                        allowedPercent,
                        blockedPercent,
                        statCards
                ),
                apiKeys,
                new DashboardPaginationDto(
                        safePage,
                        size,
                        totalApiKeys,
                        totalPages,
                        !search.isEmpty(),
                        search
                ),
                new DashboardSourcesDto(
                        "request_stats totals, api_keys metadata and persisted counters",
                        "live window counters and block markers"
                ),
                java.time.Instant.now().toString()
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
        ApiKey created = apiKeyService.createApiKey(
                request.getUserName(),
                request.getRateLimit(),
                request.getWindowSeconds()
        );
        dashboardCache.clear();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Invalid request.";
        FieldError error = ex.getBindingResult().getFieldError();
        if (error != null && error.getDefaultMessage() != null) {
            message = error.getDefaultMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Invalid request." : ex.getMessage();
        HttpStatus status = "API key not found".equalsIgnoreCase(message)
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ApiErrorResponse(message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "Service unavailable." : ex.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new ApiErrorResponse(message));
    }

    @GetMapping("/analytics/view")
    public ResponseEntity<AnalyticsViewResponse> getAnalyticsView() {
        List<ApiKeyStatsItemDto> raw = apiKeyService.getApiKeyStats();
        List<ApiKeyStatsItemDto> top = raw.stream()
                .sorted(Comparator.comparingLong(ApiKeyStatsItemDto::totalRequests).reversed())
                .limit(7)
                .toList();

        List<String> labels = top.stream()
                .map(item -> item.userName() == null || item.userName().isBlank() ? "Unknown" : item.userName())
                .toList();

        List<Long> totals = top.stream()
                .map(ApiKeyStatsItemDto::totalRequests)
                .toList();

        List<Long> allowed = top.stream()
                .map(ApiKeyStatsItemDto::allowedRequests)
                .toList();

        List<Long> blocked = top.stream()
                .map(ApiKeyStatsItemDto::blockedRequests)
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

        return ResponseEntity.ok(new AnalyticsViewResponse(
                labels,
                totals,
                allowed,
                blocked,
                new AnalyticsSummaryDto(sumTotal, sumAllowed, sumBlocked),
                maxValue
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

    private static DashboardStatCardDto statCard(
            String title,
            long value,
            String caption,
            String changeLabel,
            String trend,
            String color,
            String iconKey
    ) {
        return new DashboardStatCardDto(
                title,
                value,
                NumberFormat.getIntegerInstance().format(value),
                caption,
                changeLabel,
                trend,
                color,
                iconKey
        );
    }

    private static String formatPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0%";
        }
        return value >= 10.0
                ? String.format(java.util.Locale.ROOT, "%.1f%%", value)
                : String.format(java.util.Locale.ROOT, "%.2f%%", value);
    }

    private static String formatUsagePercentage(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0%";
        }
        if (value == 100.0d) {
            return "100%";
        }
        String formatted = value >= 10.0d
                ? String.format(java.util.Locale.ROOT, "%.1f", value)
                : String.format(java.util.Locale.ROOT, "%.2f", value);
        return formatted.replaceAll("\\.0+$", "").replaceAll("(\\.\\d*[1-9])0+$", "$1") + "%";
    }

    private static String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase();
    }

    private static String dashboardCacheKey(String search, int page, int size) {
        return search + "|" + page + "|" + size;
    }

    private record CachedDashboardView(DashboardViewResponse payload, long createdAtMs) {}
}
