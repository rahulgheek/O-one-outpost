package com.aegis.analytics_service.controller;

import com.aegis.analytics_service.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SystemHealthService healthService;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @GetMapping("/check")
    public Map<String, String> checkStatus(
            @RequestHeader(value = "loggedInUser", defaultValue = "anonymous") String user) {
        return Map.of(
                "status", "service is up",
                "detected user", user,
                "message", "Aegis System secure"
        );
    }

    @GetMapping(value = "/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDashboardData() {
        // 10-minute timeout — client will reconnect via the 5s retry in the frontend
        SseEmitter emitter = new SseEmitter(600_000L);

        ScheduledFuture<?>[] taskHolder = new ScheduledFuture<?>[1];

        taskHolder[0] = scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, String> healthStatus = healthService.checkAllServices();
                Map<String, Integer> traffic = healthService.getTrafficData();

                Map<String, Object> payload = Map.of(
                        "health", healthStatus,
                        "traffic", traffic,
                        "alerts", List.of()
                );

                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(payload, MediaType.APPLICATION_JSON));

            } catch (IOException e) {
                // Client disconnected
                if (taskHolder[0] != null) taskHolder[0].cancel(false);
                emitter.complete();
            } catch (Exception e) {
                if (taskHolder[0] != null) taskHolder[0].cancel(false);
                emitter.completeWithError(e);
            }
        }, 0, 2, TimeUnit.SECONDS);

        emitter.onCompletion(() -> { if (taskHolder[0] != null) taskHolder[0].cancel(false); });
        emitter.onTimeout(() -> { if (taskHolder[0] != null) taskHolder[0].cancel(false); });
        emitter.onError(e -> { if (taskHolder[0] != null) taskHolder[0].cancel(false); });

        return emitter;
    }
}
