package com.system.ratelimiter.dto;

import java.util.List;

public record PublicConfigResponse(
        String grafanaDashboardUrl,
        List<String> grafanaDashboardUrls,
        int refreshIntervalMs,
        List<String> allowedAlgorithms,
        PublicUiDefaultsDto defaults
) {}
