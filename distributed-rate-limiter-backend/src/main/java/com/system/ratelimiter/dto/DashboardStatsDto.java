package com.system.ratelimiter.dto;

import java.util.List;

public record DashboardStatsDto(
        long totalRequests,
        String totalRequestsLabel,
        long allowedRequests,
        String allowedRequestsLabel,
        long blockedRequests,
        String blockedRequestsLabel,
        double totalPercent,
        double allowedPercent,
        double blockedPercent,
        List<DashboardStatCardDto> cards
) {}
