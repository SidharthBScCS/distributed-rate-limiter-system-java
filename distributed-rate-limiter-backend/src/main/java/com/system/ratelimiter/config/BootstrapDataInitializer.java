package com.system.ratelimiter.config;

import com.system.ratelimiter.entity.ApiKey;
import com.system.ratelimiter.entity.RequestStats;
import com.system.ratelimiter.repository.ApiKeyRepository;
import com.system.ratelimiter.repository.RequestStatsRepository;
import com.system.ratelimiter.service.ApiKeyService;
import com.system.ratelimiter.service.RequestStatsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapDataInitializer {

    @Bean
    CommandLineRunner seedDashboardData(
            ApiKeyRepository apiKeyRepository,
            RequestStatsRepository requestStatsRepository,
            ApiKeyService apiKeyService,
            RequestStatsService requestStatsService,
            @Value("${app.bootstrap.seed-data:true}") boolean seedData
    ) {
        return args -> {
            if (!seedData || apiKeyRepository.count() > 0) {
                return;
            }

            apiKeyRepository.save(sampleApiKey(
                    "Payment Gateway",
                    120,
                    60,
                    "8a7f6d5c4b3a29100112233445566778",
                    182L,
                    171L,
                    11L,
                    "Warning"
            ));
            apiKeyRepository.save(sampleApiKey(
                    "Mobile App",
                    80,
                    60,
                    "f1e2d3c4b5a697887766554433221100",
                    96L,
                    91L,
                    5L,
                    "Normal"
            ));
            apiKeyRepository.save(sampleApiKey(
                    "Partner Portal",
                    150,
                    120,
                    "1234abcd5678efgh9012ijkl3456mnop",
                    244L,
                    230L,
                    14L,
                    "Blocked"
            ));

            RequestStats stats = requestStatsRepository.findTopByOrderByIdAsc().orElseGet(RequestStats::new);
            if (safe(stats.getTotalRequests()) == 0L
                    && safe(stats.getAllowedRequests()) == 0L
                    && safe(stats.getBlockedRequests()) == 0L) {
                stats.setTotalRequests(522L);
                stats.setAllowedRequests(492L);
                stats.setBlockedRequests(30L);
                requestStatsRepository.save(stats);
            }

            apiKeyService.refreshCache();
            requestStatsService.reloadFromDatabase();
        };
    }

    private static ApiKey sampleApiKey(
            String userName,
            int rateLimit,
            int windowSeconds,
            String token,
            long totalRequests,
            long allowedRequests,
            long blockedRequests,
            String status
    ) {
        ApiKey apiKey = new ApiKey();
        apiKey.setUserName(userName);
        apiKey.setRateLimit(rateLimit);
        apiKey.setWindowSeconds(windowSeconds);
        apiKey.setAlgorithm("SLIDING_WINDOW");
        apiKey.setApiKey(token);
        apiKey.setStatus(status);
        apiKey.setTotalRequests(totalRequests);
        apiKey.setAllowedRequests(allowedRequests);
        apiKey.setBlockedRequests(blockedRequests);
        return apiKey;
    }

    private static long safe(Long value) {
        return value == null ? 0L : value;
    }
}
