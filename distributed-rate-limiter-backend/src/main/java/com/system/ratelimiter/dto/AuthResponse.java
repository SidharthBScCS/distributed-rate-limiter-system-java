package com.system.ratelimiter.dto;

import java.time.Instant;

public record AuthResponse(
        String userId,
        String fullName,
        String email,
        Instant createdAt,
        String initials
) {
}
