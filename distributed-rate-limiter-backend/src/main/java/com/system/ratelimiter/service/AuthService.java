package com.system.ratelimiter.service;

import java.util.Objects;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final String adminUsername;
    private final String adminPassword;

    public AuthService(
            @Value("${auth.admin.username:admin}") String adminUsername,
            @Value("${auth.admin.password:admin@123}") String adminPassword
    ) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public boolean authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return false;
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        String configuredUsername = adminUsername == null ? "" : adminUsername.trim().toLowerCase(Locale.ROOT);
        String providedPassword = password.trim();
        String configuredPassword = adminPassword == null ? "" : adminPassword.trim();
        return Objects.equals(configuredUsername, normalizedUsername) && Objects.equals(configuredPassword, providedPassword);
    }
}
