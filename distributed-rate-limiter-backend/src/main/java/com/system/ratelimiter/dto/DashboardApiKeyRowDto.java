package com.system.ratelimiter.dto;

public record DashboardApiKeyRowDto(
        Long id,
        String apiKey,
        String userName,
        Integer rateLimit,
        Integer windowSeconds,
        String algorithm,
        long requestCount,
        double usagePercentage,
        String status
) {}
