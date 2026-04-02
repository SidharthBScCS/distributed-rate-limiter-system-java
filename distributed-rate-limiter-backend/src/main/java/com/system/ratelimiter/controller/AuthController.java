package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.LoginRequest;
import com.system.ratelimiter.dto.UpdateAdminProfileRequest;
import com.system.ratelimiter.service.AuthService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final String adminUsername;
    private volatile String adminFullName;
    private volatile String adminEmail;
    private final Instant createdAt;

    public AuthController(
            AuthService authService,
            @Value("${auth.admin.username:admin}") String adminUsername,
            @Value("${auth.admin.full-name:System Admin}") String adminFullName,
            @Value("${auth.admin.email:admin@ratelimiter.local}") String adminEmail
    ) {
        this.authService = authService;
        this.adminUsername = adminUsername;
        this.adminFullName = adminFullName;
        this.adminEmail = adminEmail;
        this.createdAt = Instant.now();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        boolean ok = authService.authenticate(request.getUsername(), request.getPassword());
        if (!ok) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        HttpSession existing = servletRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute("userId", adminUsername);
        Map<String, Object> body = adminPayload();
        body.put("message", "Login successful");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/admin/{userId}")
    public ResponseEntity<Map<String, Object>> getAdmin(@PathVariable("userId") String userId) {
        if (!adminUsername.equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Admin not found"));
        }
        return ResponseEntity.ok(adminPayload());
    }

    @GetMapping("/admins")
    public ResponseEntity<Map<String, Object>> listAdmins() {
        var admins = java.util.List.of(adminListItemPayload());
        return ResponseEntity.ok(Map.of(
                "count", admins.size(),
                "items", admins
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentAdmin(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }
        if (!adminUsername.equals(String.valueOf(userId))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }
        return ResponseEntity.ok(adminPayload());
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateCurrentAdmin(
            @Valid @RequestBody UpdateAdminProfileRequest request,
            HttpSession session
    ) {
        Object userId = session.getAttribute("userId");
        if (userId == null || !adminUsername.equals(String.valueOf(userId))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }

        adminFullName = request.getFullName().trim();
        adminEmail = request.getEmail().trim();

        Map<String, Object> body = adminPayload();
        body.put("message", "Profile updated");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Authentication failed unexpectedly"));
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

    private Map<String, Object> adminPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", adminUsername);
        payload.put("fullName", adminFullName);
        payload.put("email", adminEmail);
        payload.put("createdAt", createdAt);
        payload.put("initials", initials(adminFullName, adminUsername));
        return payload;
    }

    private Map<String, Object> adminListItemPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", adminUsername);
        payload.put("fullName", adminFullName);
        payload.put("email", adminEmail);
        payload.put("createdAt", createdAt);
        return payload;
    }
}
