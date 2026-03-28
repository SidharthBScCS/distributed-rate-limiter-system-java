package com.system.ratelimiter.dto;

public record RedisHealthResponse(
        String service,
        String status,
        String ping,
        String error
) {}
