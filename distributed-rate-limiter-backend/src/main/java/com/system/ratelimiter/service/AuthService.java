package com.system.ratelimiter.service;

import com.system.ratelimiter.dto.AuthResponse;
import com.system.ratelimiter.entity.Administrator;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AdministratorService administratorService;
    private final AdministratorPrincipalService administratorPrincipalService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AdministratorService administratorService,
            AdministratorPrincipalService administratorPrincipalService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.administratorService = administratorService;
        this.administratorPrincipalService = administratorPrincipalService;
    }

    public AuthResponse authenticate(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username == null ? "" : username.trim(), password)
            );
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            Administrator administrator = administratorService.getByUsername(principal.getUsername());
            String token = jwtService.generateToken(principal, Map.of());
            return toAuthResponse(administrator, token);
        } catch (BadCredentialsException ex) {
            throw ex;
        }
    }

    public AuthResponse getCurrentAdmin(String username) {
        Administrator administrator = administratorService.getByUsername(username);
        UserDetails userDetails = administratorPrincipalService.toUserDetails(administrator);
        String token = jwtService.generateToken(userDetails, Map.of());
        return toAuthResponse(administrator, token);
    }

    public AuthResponse updateProfile(String username) {
        return getCurrentAdmin(username);
    }

    private AuthResponse toAuthResponse(Administrator administrator, String token) {
        return new AuthResponse(
                token,
                "Bearer",
                administrator.getUsername(),
                administrator.getUsername(),
                "",
                null,
                initials(administrator.getUsername(), administrator.getUsername())
        );
    }

    private String initials(String fullName, String fallback) {
        String base = (fullName == null || fullName.isBlank()) ? fallback : fullName;
        if (base == null || base.isBlank()) {
            return "AD";
        }
        String[] parts = base.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }
}
