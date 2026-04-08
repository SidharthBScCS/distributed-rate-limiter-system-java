package com.system.ratelimiter.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final Key signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret(secret));
        this.expirationMs = Math.max(60000L, expirationMs);
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();
        return username != null
                && username.equalsIgnoreCase(userDetails.getUsername())
                && expiration != null
                && expiration.toInstant().isAfter(Instant.now());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static byte[] resolveSecret(String rawSecret) {
        String value = rawSecret == null ? "" : rawSecret.trim();
        if (value.regionMatches(true, 0, "base64:", 0, "base64:".length())) {
            String encoded = value.substring("base64:".length()).trim();
            return Decoders.BASE64.decode(encoded);
        }
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
