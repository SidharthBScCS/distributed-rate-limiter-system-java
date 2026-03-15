package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.RequestStats;
import com.system.ratelimiter.repository.RequestStatsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestStatsService {

    private final RequestStatsRepository requestStatsRepository;

    public RequestStatsService(RequestStatsRepository requestStatsRepository) {
        this.requestStatsRepository = requestStatsRepository;
    }

    public RequestStats getOrCreate() {
        RequestStats stats = requestStatsRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> {
                    RequestStats created = new RequestStats();
                    created.setTotalRequests(0L);
                    created.setAllowedRequests(0L);
                    created.setBlockedRequests(0L);
                    return requestStatsRepository.save(created);
                });
        return normalize(stats);
    }

    public RequestStats incrementAllowed(long delta) {
        RequestStats stats = normalize(getOrCreate());
        stats.setTotalRequests(safe(stats.getTotalRequests()) + delta);
        stats.setAllowedRequests(safe(stats.getAllowedRequests()) + delta);
        return requestStatsRepository.save(stats);
    }

    public RequestStats incrementBlocked(long delta) {
        RequestStats stats = normalize(getOrCreate());
        stats.setTotalRequests(safe(stats.getTotalRequests()) + delta);
        stats.setBlockedRequests(safe(stats.getBlockedRequests()) + delta);
        return requestStatsRepository.save(stats);
    }

    @PostConstruct
    @Transactional
    public void syncOnStartup() {
        getOrCreate();
    }

    public RequestStats snapshot() {
        return normalize(getOrCreate());
    }

    private RequestStats normalize(RequestStats stats) {
        boolean changed = false;
        if (stats.getTotalRequests() == null) {
            stats.setTotalRequests(0L);
            changed = true;
        }
        if (stats.getAllowedRequests() == null) {
            stats.setAllowedRequests(0L);
            changed = true;
        }
        if (stats.getBlockedRequests() == null) {
            stats.setBlockedRequests(0L);
            changed = true;
        }
        return changed ? requestStatsRepository.save(stats) : stats;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }
}
