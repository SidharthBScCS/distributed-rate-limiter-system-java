package com.system.ratelimiter.dto;

import java.util.List;

public record DashboardViewResponse(
        DashboardStatsDto stats,
        List<DashboardApiKeyRowDto> apiKeys,
        DashboardPaginationDto pagination,
        DashboardSourcesDto sources,
        String generatedAt
) {}
