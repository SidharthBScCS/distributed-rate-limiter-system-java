package com.system.ratelimiter.dto;

public record PublicUiDefaultsDto(
        int rateLimit,
        int windowSeconds,
        String algorithm
) {}
