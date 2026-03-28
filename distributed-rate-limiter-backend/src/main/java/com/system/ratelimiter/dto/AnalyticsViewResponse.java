package com.system.ratelimiter.dto;

import java.util.List;

public record AnalyticsViewResponse(
        List<String> labels,
        List<Long> totalRequests,
        List<Long> successRequests,
        List<Long> blockedRequests,
        AnalyticsSummaryDto summary,
        long maxValue
) {}
