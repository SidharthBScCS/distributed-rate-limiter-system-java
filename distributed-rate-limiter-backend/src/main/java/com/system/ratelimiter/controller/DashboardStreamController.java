package com.system.ratelimiter.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    public DashboardStreamController(@Value("${ui.refresh-interval-ms:1000}") long intervalMs) {
        this.intervalMs = Math.max(1000L, intervalMs);
    }

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDashboardTicks() {
        SseEmitter emitter = new SseEmitter(0L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        emitter.onCompletion(executor::shutdownNow);
        emitter.onTimeout(() -> {
            emitter.complete();
            executor.shutdownNow();
        });
        emitter.onError((ex) -> executor.shutdownNow());

        executor.execute(() -> {
            try {
                // Initial event ensures UI can react immediately.
                emitter.send(SseEmitter.event()
                        .name("tick")
                        .data(Map.of("ts", System.currentTimeMillis())));

                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(intervalMs);
                    emitter.send(SseEmitter.event()
                            .name("tick")
                            .data(Map.of("ts", System.currentTimeMillis())));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ioException) {
                // Connection closed by client.
            } finally {
                emitter.complete();
                executor.shutdownNow();
            }
        });

        return emitter;
    }
}
