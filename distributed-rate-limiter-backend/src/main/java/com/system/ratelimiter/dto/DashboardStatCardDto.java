package com.system.ratelimiter.dto;

public record DashboardStatCardDto(
        String title,
        long value,
        String valueLabel,
        String caption,
        String changeLabel,
        String trend,
        String color,
        String iconKey
) {}
