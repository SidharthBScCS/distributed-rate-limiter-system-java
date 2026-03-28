package com.system.ratelimiter.dto;

public record DashboardPaginationDto(
        int page,
        int size,
        int totalItems,
        int totalPages,
        boolean filtered,
        String search
) {}
