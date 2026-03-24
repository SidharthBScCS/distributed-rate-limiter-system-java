package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.RateLimitCheckRequest;
import com.system.ratelimiter.dto.RateLimitDecisionResponse;
import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.entity.RequestStats;
import com.system.ratelimiter.service.ApiKeyService;
import com.system.ratelimiter.service.DecisionAuditService;
import com.system.ratelimiter.service.DistributedRateLimiterService;
import com.system.ratelimiter.service.RequestStatsService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        controller = new ApiKeyController(
                apiKeyService,
                requestStatsService,
                distributedRateLimiterService,
                decisionAuditService,
                500L
        );
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

    @Test
    void getDashboardView_appliesSearchAndPaginationOnBackend() {
        ApiKey alpha = new ApiKey();
        alpha.setId(1L);
        alpha.setApiKey("key-alpha");
        alpha.setUserName("Alpha");
        alpha.setRateLimit(10);
        alpha.setWindowSeconds(60);
        alpha.setAlgorithm("SLIDING_WINDOW");

        ApiKey beta = new ApiKey();
        beta.setId(2L);
        beta.setApiKey("key-beta");
        beta.setUserName("Beta");
        beta.setRateLimit(20);
        beta.setWindowSeconds(60);
        beta.setAlgorithm("SLIDING_WINDOW");

        RequestStats stats = new RequestStats();
        stats.setTotalRequests(15L);
        stats.setAllowedRequests(12L);
        stats.setBlockedRequests(3L);

        when(requestStatsService.snapshot()).thenReturn(stats);
        when(apiKeyService.getAllRealKeys()).thenReturn(List.of(alpha, beta));
        when(distributedRateLimiterService.resolveCurrentWindowRequestCount(alpha)).thenReturn(4L);
        when(distributedRateLimiterService.resolveCurrentWindowRequestCount(beta)).thenReturn(8L);
        when(distributedRateLimiterService.resolveCurrentStatus(alpha)).thenReturn("Normal");
        when(distributedRateLimiterService.resolveCurrentStatus(beta)).thenReturn("Blocked");

        ResponseEntity<Map<String, Object>> response = controller.getDashboardView("beta", 1, 1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        List<?> apiKeys = (List<?>) response.getBody().get("apiKeys");
        assertEquals(1, apiKeys.size());
        assertTrue(apiKeys.get(0) instanceof Map);
        Map<?, ?> row = (Map<?, ?>) apiKeys.get(0);
        assertEquals("Beta", row.get("userName"));

        assertTrue(response.getBody().get("pagination") instanceof Map);
        Map<?, ?> pagination = (Map<?, ?>) response.getBody().get("pagination");
        assertEquals(1, pagination.get("page"));
        assertEquals(1, pagination.get("size"));
        assertEquals(1, pagination.get("totalItems"));
        assertEquals("beta", pagination.get("search"));
    }
}
