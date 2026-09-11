package com.aegis.analytics_service.controller;

import com.aegis.analytics_service.model.SystemLog;
import com.aegis.analytics_service.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analytics/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final SystemLogRepository systemLogRepository;

    @PostMapping
    public ResponseEntity<Void> receiveLog(@RequestBody SystemLog logRequest) {
        if (logRequest.getTimestamp() == null) {
            logRequest.setTimestamp(Instant.now());
        }
        systemLogRepository.save(logRequest);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<SystemLog>> getLogs(@RequestParam(required = false) String serviceName) {
        List<SystemLog> logs;
        if (serviceName != null && !serviceName.isEmpty() && !serviceName.equals("All Services")) {
            logs = systemLogRepository.findByServiceNameOrderByTimestampDesc(serviceName);
        } else {
            logs = systemLogRepository.findTop100ByOrderByTimestampDesc();
        }
        return ResponseEntity.ok(logs);
    }
}
