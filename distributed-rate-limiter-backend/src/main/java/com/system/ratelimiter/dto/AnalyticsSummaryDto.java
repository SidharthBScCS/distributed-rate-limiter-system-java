package com.system.ratelimiter.dto;

public record AnalyticsSummaryDto(
        long total,
        long success,
        long blocked
) {}
