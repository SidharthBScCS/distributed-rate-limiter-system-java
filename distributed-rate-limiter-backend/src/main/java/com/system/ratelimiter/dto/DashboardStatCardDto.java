package com.system.ratelimiter.dto;

public record DashboardStatCardDto(
        String title,
        long value,
        String caption,
        double percentage
) {}
