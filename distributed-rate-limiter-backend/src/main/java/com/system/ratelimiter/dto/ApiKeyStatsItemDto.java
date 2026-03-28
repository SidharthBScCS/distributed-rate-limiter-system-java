package com.system.ratelimiter.dto;

public record ApiKeyStatsItemDto(
        String userName,
        long totalRequests,
        long allowedRequests,
        long blockedRequests,
        String algorithm
) {}
