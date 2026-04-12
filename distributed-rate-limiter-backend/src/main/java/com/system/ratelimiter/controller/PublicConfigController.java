package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.PublicConfigResponse;
import com.system.ratelimiter.dto.PublicUiDefaultsDto;
import java.net.URI;
import java.util.LinkedHashSet;
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
    private final List<String> grafanaDashboardUrls;
    private final int refreshIntervalMs;
    private final int defaultRateLimit;
    private final int defaultWindowSeconds;
    private final String defaultAlgorithm;
    private final List<String> allowedAlgorithms;

    public PublicConfigController(
            @Value("${ui.grafana.dashboard-url:}") String grafanaDashboardUrl,
            @Value("${ui.refresh-interval-ms:5000}") int refreshIntervalMs,
            @Value("${ui.defaults.rate-limit:10}") int defaultRateLimit,
            @Value("${ui.defaults.window-seconds:60}") int defaultWindowSeconds
    ) {
        this.grafanaDashboardUrl = normalizeGrafanaDashboardUrl(grafanaDashboardUrl);
        this.grafanaDashboardUrls = buildGrafanaDashboardUrls(this.grafanaDashboardUrl);
        this.refreshIntervalMs = Math.max(5000, refreshIntervalMs);
        this.defaultRateLimit = Math.max(1, defaultRateLimit);
        this.defaultWindowSeconds = Math.max(1, defaultWindowSeconds);
        this.defaultAlgorithm = "SLIDING_WINDOW";
        this.allowedAlgorithms = List.of("SLIDING_WINDOW");
    }

    @GetMapping("/config")
    public ResponseEntity<PublicConfigResponse> getFrontendConfig() {
        return ResponseEntity.ok(new PublicConfigResponse(
                grafanaDashboardUrl,
                grafanaDashboardUrls,
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

    private static List<String> buildGrafanaDashboardUrls(String primaryUrl) {
        if (primaryUrl == null || primaryUrl.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.add(primaryUrl);

        try {
            URI uri = URI.create(primaryUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            boolean isLocal = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            if (isLocal && (port == 3001 || port == 3002)) {
                int fallbackPort = port == 3001 ? 3002 : 3001;
                URI fallback = new URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        uri.getHost(),
                        fallbackPort,
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                );
                urls.add(fallback.toString());
            }
        } catch (Exception ignored) {
            // Keep the primary URL only when parsing fails.
        }

        return List.copyOf(urls);
    }
}
