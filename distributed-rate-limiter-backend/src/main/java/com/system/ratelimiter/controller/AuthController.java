package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.LoginRequest;
import com.system.ratelimiter.dto.AuthResponse;
import com.system.ratelimiter.dto.UpdateAdminProfileRequest;
import com.system.ratelimiter.service.AuthService;
import com.system.ratelimiter.service.AdministratorService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public AuthController(
            AuthService authService,
            AdministratorService administratorService
    ) {
        this.authService = authService;
        this.administratorService = administratorService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.authenticate(request.getUsername(), request.getPassword()));
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
    public ResponseEntity<AuthResponse> getCurrentAdmin(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentAdmin(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<AuthResponse> updateCurrentAdmin(
            @Valid @RequestBody UpdateAdminProfileRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                authService.updateProfile(authentication.getName(), request.getFullName(), request.getEmail())
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
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
        payload.put("role", administrator.getRole());
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
}
