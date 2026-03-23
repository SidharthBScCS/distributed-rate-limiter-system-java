package com.system.ratelimiter.service;

import com.system.ratelimiter.entity.RequestStats;
import com.system.ratelimiter.repository.RequestStatsRepository;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestStatsService {

    private final RequestStatsRepository requestStatsRepository;
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong allowedRequests = new AtomicLong();
    private final AtomicLong blockedRequests = new AtomicLong();
    private final AtomicLong pendingTotalDelta = new AtomicLong();
    private final AtomicLong pendingAllowedDelta = new AtomicLong();
    private final AtomicLong pendingBlockedDelta = new AtomicLong();
    private final long flushIntervalMs;
    private volatile Long statsId;

    public RequestStatsService(
            RequestStatsRepository requestStatsRepository,
            @Value("${ratelimiter.stats.flush-ms:5000}") long flushIntervalMs
    ) {
        this.requestStatsRepository = requestStatsRepository;
        this.flushIntervalMs = Math.max(1000L, flushIntervalMs);
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
        long safeDelta = Math.max(0L, delta);
        totalRequests.addAndGet(safeDelta);
        allowedRequests.addAndGet(safeDelta);
        pendingTotalDelta.addAndGet(safeDelta);
        pendingAllowedDelta.addAndGet(safeDelta);
        return snapshot();
    }

    public RequestStats incrementBlocked(long delta) {
        long safeDelta = Math.max(0L, delta);
        totalRequests.addAndGet(safeDelta);
        blockedRequests.addAndGet(safeDelta);
        pendingTotalDelta.addAndGet(safeDelta);
        pendingBlockedDelta.addAndGet(safeDelta);
        return snapshot();
    }

    @PostConstruct
    @Transactional
    public void syncOnStartup() {
        RequestStats stats = normalize(getOrCreate());
        statsId = stats.getId();
        totalRequests.set(safe(stats.getTotalRequests()));
        allowedRequests.set(safe(stats.getAllowedRequests()));
        blockedRequests.set(safe(stats.getBlockedRequests()));
    }

    public RequestStats snapshot() {
        RequestStats snapshot = new RequestStats();
        snapshot.setTotalRequests(totalRequests.get());
        snapshot.setAllowedRequests(allowedRequests.get());
        snapshot.setBlockedRequests(blockedRequests.get());
        return snapshot;
    }

    @Scheduled(fixedDelayString = "${ratelimiter.stats.flush-ms:5000}")
    @Transactional
    public void flushPending() {
        long totalDelta = pendingTotalDelta.getAndSet(0L);
        long allowedDelta = pendingAllowedDelta.getAndSet(0L);
        long blockedDelta = pendingBlockedDelta.getAndSet(0L);
        if (totalDelta == 0L && allowedDelta == 0L && blockedDelta == 0L) {
            return;
        }

        RequestStats stats = getOrCreate();
        if (statsId == null) {
            statsId = stats.getId();
        }

        int updated = requestStatsRepository.incrementCounters(statsId, totalDelta, allowedDelta, blockedDelta);
        if (updated == 0) {
            RequestStats current = normalize(getOrCreate());
            statsId = current.getId();
            requestStatsRepository.incrementCounters(statsId, totalDelta, allowedDelta, blockedDelta);
        }
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
