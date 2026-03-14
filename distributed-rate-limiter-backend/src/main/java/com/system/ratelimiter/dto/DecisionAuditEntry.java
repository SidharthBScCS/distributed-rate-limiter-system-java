package com.system.ratelimiter.dto;

public record DecisionAuditEntry(
        String apiKey,
        String route,
        boolean allowed,
        String reason,
        String algorithm,
        int retryAfterSeconds,
        int limit,
        int windowSeconds,
        long currentUsage,
        long remainingRequests,
        String evaluatedAt
) {}
