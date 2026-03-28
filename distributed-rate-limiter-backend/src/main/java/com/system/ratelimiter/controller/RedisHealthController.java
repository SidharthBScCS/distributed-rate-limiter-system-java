package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.RedisHealthResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class RedisHealthController {

    private final StringRedisTemplate redisTemplate;

    public RedisHealthController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/redis")
    public ResponseEntity<RedisHealthResponse> redisHealth() {
        try {
            String pong = redisTemplate.execute((RedisConnection connection) -> connection.ping());
            boolean up = "PONG".equalsIgnoreCase(pong);
            HttpStatus status = up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(new RedisHealthResponse(
                    "redis",
                    up ? "UP" : "DOWN",
                    pong == null ? "" : pong,
                    null
            ));
        } catch (DataAccessException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new RedisHealthResponse(
                    "redis",
                    "DOWN",
                    "",
                    ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage()
            ));
        }
    }
}
