package com.system.ratelimiter.dto;

public record DashboardSourcesDto(
        String postgres,
        String redis
) {}
