package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.PublicConfigResponse;
import com.system.ratelimiter.dto.PublicUiDefaultsDto;
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
            @Value("${ui.refresh-interval-ms:500}") int refreshIntervalMs,
            @Value("${ui.defaults.rate-limit:10}") int defaultRateLimit,
            @Value("${ui.defaults.window-seconds:60}") int defaultWindowSeconds
    ) {
        this.grafanaDashboardUrl = normalizeGrafanaDashboardUrl(grafanaDashboardUrl);
        this.refreshIntervalMs = Math.max(500, refreshIntervalMs);
        this.defaultRateLimit = Math.max(1, defaultRateLimit);
        this.defaultWindowSeconds = Math.max(1, defaultWindowSeconds);
        this.defaultAlgorithm = "SLIDING_WINDOW";
        this.allowedAlgorithms = List.of("SLIDING_WINDOW");
    }

    @GetMapping("/config")
    public ResponseEntity<PublicConfigResponse> getFrontendConfig() {
        return ResponseEntity.ok(new PublicConfigResponse(
                grafanaDashboardUrl,
                refreshIntervalMs,
                allowedAlgorithms,
                new PublicUiDefaultsDto(
                        defaultRateLimit,
                        defaultWindowSeconds,
                        defaultAlgorithm
                )
        ));
    }

    private static String normalizeGrafanaDashboardUrl(String value) {
        if (value == null) {
            return "";
        }
        String url = value.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (url.contains("kiosk=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "kiosk=tv";
    }
}
