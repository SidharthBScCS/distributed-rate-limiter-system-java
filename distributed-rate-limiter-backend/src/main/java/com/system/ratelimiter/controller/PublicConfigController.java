package com.system.ratelimiter.controller;

import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PublicConfigController {

    private final String grafanaDashboardUrl;
    private final int refreshIntervalMs;
    private final int defaultRateLimit;
    private final int defaultWindowSeconds;
    private final String defaultAlgorithm;
    private final List<String> allowedAlgorithms;

    public PublicConfigController(
            @Value("${ui.grafana.dashboard-url:}") String grafanaDashboardUrl,
            @Value("${ui.refresh-interval-ms:30000}") int refreshIntervalMs,
            @Value("${ui.defaults.rate-limit:10}") int defaultRateLimit,
            @Value("${ui.defaults.window-seconds:60}") int defaultWindowSeconds,
            @Value("${ui.defaults.algorithm:SLIDING_WINDOW}") String defaultAlgorithm
    ) {
        this.grafanaDashboardUrl = grafanaDashboardUrl;
        this.refreshIntervalMs = Math.max(5000, refreshIntervalMs);
        this.defaultRateLimit = Math.max(1, defaultRateLimit);
        this.defaultWindowSeconds = Math.max(1, defaultWindowSeconds);
        this.defaultAlgorithm = "SLIDING_WINDOW";
        this.allowedAlgorithms = List.of("SLIDING_WINDOW");
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getFrontendConfig() {
        return ResponseEntity.ok(Map.of(
                "grafanaDashboardUrl", grafanaDashboardUrl,
                "refreshIntervalMs", refreshIntervalMs,
                "allowedAlgorithms", allowedAlgorithms,
                "defaults", Map.of(
                        "rateLimit", defaultRateLimit,
                        "windowSeconds", defaultWindowSeconds,
                        "algorithm", defaultAlgorithm
                )
        ));
    }
}
