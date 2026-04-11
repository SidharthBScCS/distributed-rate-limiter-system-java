package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.LoginRequest;
import com.system.ratelimiter.dto.AuthResponse;
import com.system.ratelimiter.dto.UpdateAdminProfileRequest;
import com.system.ratelimiter.service.AuthService;
import com.system.ratelimiter.service.AdministratorService;
import jakarta.validation.Valid;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AdministratorService administratorService;
    private final String authCookieName;
    private final boolean authCookieSecure;
    private final String authCookieSameSite;
    private final long authCookieMaxAgeSeconds;

    public AuthController(
            AuthService authService,
            AdministratorService administratorService,
            @Value("${auth.cookie.name:RL_ADMIN_TOKEN}") String authCookieName,
            @Value("${auth.cookie.secure:false}") boolean authCookieSecure,
            @Value("${auth.cookie.same-site:Lax}") String authCookieSameSite,
            @Value("${jwt.expiration-ms:86400000}") long jwtExpirationMs
    ) {
        this.authService = authService;
        this.administratorService = administratorService;
        this.authCookieName = authCookieName;
        this.authCookieSecure = authCookieSecure;
        this.authCookieSameSite = authCookieSameSite;
        this.authCookieMaxAgeSeconds = Math.max(1L, jwtExpirationMs / 1000L);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.AuthSession session = authService.authenticate(request.getUsername(), request.getPassword());
        writeAuthCookie(response, session.token());
        return ResponseEntity.ok(session.response());
    }

    @GetMapping("/admin/{userId}")
    public ResponseEntity<Map<String, Object>> getAdmin(@PathVariable("userId") String userId) {
        try {
            return ResponseEntity.ok(toAdminPayload(administratorService.getByUsername(userId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Admin not found"));
        }
    }

    @GetMapping("/admins")
    public ResponseEntity<Map<String, Object>> listAdmins() {
        var admins = administratorService.findAll().stream()
                .map(this::toAdminListItemPayload)
                .toList();
        return ResponseEntity.ok(Map.of(
                "count", admins.size(),
                "items", admins
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentAdmin(Authentication authentication, HttpServletResponse response) {
        AuthService.AuthSession session = authService.getCurrentAdmin(authentication.getName());
        writeAuthCookie(response, session.token());
        return ResponseEntity.ok(session.response());
    }

    @PutMapping("/me")
    public ResponseEntity<AuthResponse> updateCurrentAdmin(
            @Valid @RequestBody UpdateAdminProfileRequest request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        AuthService.AuthSession session = authService.updateProfile(
                authentication.getName(),
                request.getFullName(),
                request.getEmail()
        );
        writeAuthCookie(response, session.token());
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        clearAuthCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = "Invalid credentials";
        FieldError error = ex.getBindingResult().getFieldError();
        if (error != null && error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) {
            message = error.getDefaultMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("message", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("message", "Invalid JSON payload"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid credentials"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Authentication failed unexpectedly"));
    }

    private Map<String, Object> toAdminPayload(com.system.ratelimiter.entity.Administrator administrator) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("userId", administrator.getUsername());
        payload.put("fullName", administrator.getFullName());
        payload.put("email", administrator.getEmail());
        payload.put("createdAt", administrator.getCreatedAt());
        return payload;
    }

    private Map<String, Object> toAdminListItemPayload(com.system.ratelimiter.entity.Administrator administrator) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("userId", administrator.getUsername());
        payload.put("fullName", administrator.getFullName());
        payload.put("email", administrator.getEmail());
        payload.put("createdAt", administrator.getCreatedAt());
        return payload;
    }

    private void writeAuthCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(authCookieName, token)
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/")
                .maxAge(authCookieMaxAgeSeconds)
                .build()
                .toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(authCookieName, "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path("/")
                .maxAge(0)
                .build()
                .toString());
    }
}
