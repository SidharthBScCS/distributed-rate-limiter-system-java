package com.system.ratelimiter.controller;

import com.system.ratelimiter.dto.DecisionAuditEntry;
import com.system.ratelimiter.service.DecisionAuditService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class DashboardStreamController {

    private final long intervalMs;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DashboardStreamController(
            @Value("${ui.refresh-interval-ms:500}") long intervalMs,
            DecisionAuditService decisionAuditService
    ) {
        this.intervalMs = Math.max(500L, intervalMs);
        scheduler.scheduleAtFixedRate(this::broadcastTick, 0L, this.intervalMs, TimeUnit.MILLISECONDS);
        decisionAuditService.registerBlockedDecisionListener(this::broadcastBlockedDecision);
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDashboardEvents() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError((ex) -> emitters.remove(emitter));

        sendEvent(emitter, "ready", Map.of(
                "refreshIntervalMs", intervalMs,
                "ts", System.currentTimeMillis()
        ));
        sendEvent(emitter, "tick", Map.of("ts", System.currentTimeMillis()));
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
        emitters.clear();
    }

    private void broadcastTick() {
        broadcast("tick", Map.of("ts", System.currentTimeMillis()));
    }

    private void broadcastBlockedDecision(DecisionAuditEntry entry) {
        String apiKeyValue = entry.apiKey() == null ? "" : entry.apiKey();
        String shortKey = apiKeyValue.length() <= 12 ? apiKeyValue : apiKeyValue.substring(0, 12);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", apiKeyValue + "-" + entry.evaluatedAt());
        payload.put("title", "Too many requests (429)");
        payload.put("message", "API key " + shortKey + " exceeded the sliding window limit.");
        payload.put("apiKey", apiKeyValue);
        payload.put("evaluatedAt", entry.evaluatedAt());
        payload.put("route", entry.route() == null ? "" : entry.route());
        payload.put("reason", entry.reason() == null ? "" : entry.reason());
        broadcast("alert", payload);
    }

    private void broadcast(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            sendEvent(emitter, eventName, payload);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException ex) {
            emitters.remove(emitter);
            emitter.complete();
        }
    }
}
