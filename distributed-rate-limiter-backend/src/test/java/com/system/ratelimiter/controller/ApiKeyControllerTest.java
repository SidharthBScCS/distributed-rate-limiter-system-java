package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.RateLimitCheckRequest;
import com.system.ratelimiter.dto.RateLimitDecisionResponse;
import com.system.ratelimiter.service.ApiKeyService;
import com.system.ratelimiter.service.DecisionAuditService;
import com.system.ratelimiter.service.DistributedRateLimiterService;
import com.system.ratelimiter.service.RequestStatsService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private RequestStatsService requestStatsService;

    @Mock
    private DistributedRateLimiterService distributedRateLimiterService;

    @Mock
    private DecisionAuditService decisionAuditService;

    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiKeyController(apiKeyService, requestStatsService, distributedRateLimiterService, decisionAuditService);
    }

    @Test
    void checkLimit_whenDeniedWithAllowedReason_normalizesReasonAndReturns429() {
        RateLimitCheckRequest request = new RateLimitCheckRequest();
        request.setApiKey("k1");
        request.setRoute("/api/test");
        request.setTokens(1);

        when(distributedRateLimiterService.evaluate(anyString(), anyString(), anyInt()))
                .thenReturn(new DistributedRateLimiterService.Decision(
                        false,
                        60,
                        "ALLOWED",
                        "SLIDING_WINDOW",
                        10,
                        60,
                        10L,
                        0L,
                        Instant.parse("2026-03-14T00:00:00Z"),
                        "/api/test"
                ));

        ResponseEntity<RateLimitDecisionResponse> response = controller.checkLimit(request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().allowed());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().reason());
        assertEquals(0L, response.getBody().remainingRequests());
    }

    @Test
    void checkLimit_whenAllowedWithEmptyReason_returnsAllowedReason() {
        RateLimitCheckRequest request = new RateLimitCheckRequest();
        request.setApiKey("k1");
        request.setRoute("/api/test");
        request.setTokens(1);

        when(distributedRateLimiterService.evaluate(anyString(), anyString(), anyInt()))
                .thenReturn(new DistributedRateLimiterService.Decision(
                        true,
                        0,
                        "",
                        "SLIDING_WINDOW"
                        ,10
                        ,60
                        ,4L
                        ,6L
                        ,Instant.parse("2026-03-14T00:00:00Z")
                        ,"/api/test"
                ));

        ResponseEntity<RateLimitDecisionResponse> response = controller.checkLimit(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().allowed());
        assertEquals("ALLOWED", response.getBody().reason());
        assertEquals(6L, response.getBody().remainingRequests());
    }
}
