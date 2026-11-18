package com.aegis.arena_service.controller;

import com.aegis.arena_service.model.DailyQuestion;
import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.DailyQuestionRepository;
import com.aegis.arena_service.repository.ProblemRepository;
import com.aegis.arena_service.services.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/problems/daily")
@RequiredArgsConstructor
public class DailyQuestionController {

    private final DailyQuestionRepository dailyQuestionRepository;
    private final ProblemRepository problemRepository;
    private final ProblemService problemService;

    /**
     * PUBLIC endpoint — returns today's active daily challenge.
     *
     * Security model (LeetCode-style):
     *  - Before activatesAt → 403 FORBIDDEN ("Challenge not yet released")
     *  - After expiresAt   → 404 NOT FOUND  ("No active daily challenge")
     *  - Within window     → 200 OK with full problem payload
     *
     * The check runs ENTIRELY on the server using Instant.now().
     * Clients never receive any hint about future problem content.
     */
    @GetMapping
    public ResponseEntity<?> getDailyQuestion() {
        Instant now = Instant.now();

        // Is there an active daily question right now?
        Optional<DailyQuestion> activeOpt = dailyQuestionRepository
                .findFirstByActivatesAtLessThanEqualAndExpiresAtGreaterThan(now, now);

        if (activeOpt.isPresent()) {
            DailyQuestion active = activeOpt.get();
            return problemRepository.findById(active.getProblemId())
                    .map(problemService::withStartingCode)
                    .map(problem -> {
                        // Attach window metadata without leaking future problems
                        Map<String, Object> response = new LinkedHashMap<>();
                        response.put("problem", problem);
                        response.put("activatesAt", active.getActivatesAt());
                        response.put("expiresAt", active.getExpiresAt());
                        response.put("label", active.getLabel());
                        response.put("remainingSeconds",
                                Duration.between(now, active.getExpiresAt()).getSeconds());
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.notFound().build());
        }

        // Check if there is a FUTURE question (scheduled but not yet active)
        // We intentionally don't reveal the problem, only the countdown
        List<DailyQuestion> upcoming = dailyQuestionRepository.findAllByOrderByActivatesAtDesc()
                .stream()
                .filter(dq -> dq.getActivatesAt().isAfter(now))
                .collect(Collectors.toList());

        if (!upcoming.isEmpty()) {
            DailyQuestion next = upcoming.get(upcoming.size() - 1); // earliest future
            Map<String, Object> locked = new LinkedHashMap<>();
            locked.put("status", "LOCKED");
            locked.put("message", "The next Daily Challenge is not yet available.");
            locked.put("activatesAt", next.getActivatesAt());
            locked.put("secondsUntilRelease",
                    Duration.between(now, next.getActivatesAt()).getSeconds());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(locked);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "NONE", "message", "No active Daily Challenge scheduled."));
    }

    /**
     * ADMIN endpoint — list ALL scheduled daily questions (past, active, future).
     * Enriches each entry with the problem title for display in the admin panel.
     */
    @GetMapping("/admin/all")
    public ResponseEntity<List<Map<String, Object>>> getAllScheduled() {
        List<Map<String, Object>> enriched = dailyQuestionRepository
                .findAllByOrderByActivatesAtDesc()
                .stream()
                .map(dq -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", dq.getId());
                    entry.put("problemId", dq.getProblemId());
                    entry.put("label", dq.getLabel());
                    entry.put("activatesAt", dq.getActivatesAt());
                    entry.put("expiresAt", dq.getExpiresAt());
                    entry.put("scheduledBy", dq.getScheduledBy());
                    // Enrich with the problem title
                    problemRepository.findById(dq.getProblemId())
                            .ifPresent(p -> entry.put("problemTitle", p.getTitle()));
                    // Compute window status
                    Instant now = Instant.now();
                    if (now.isBefore(dq.getActivatesAt())) {
                        entry.put("windowStatus", "UPCOMING");
                    } else if (now.isAfter(dq.getExpiresAt())) {
                        entry.put("windowStatus", "EXPIRED");
                    } else {
                        entry.put("windowStatus", "ACTIVE");
                    }
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(enriched);
    }

    /**
     * DTO for scheduling requests.
     */
    public static class ScheduleRequest {
        public String problemId;
        public String activatesAt; // ISO-8601 string from frontend datetime-local input
        public String label;
        public String scheduledBy;
    }

    /**
     * ADMIN endpoint — schedule a problem as the next daily question.
     * Automatically sets expiresAt = activatesAt + 24 hours.
     */
    @PostMapping("/admin/schedule")
    public ResponseEntity<?> scheduleDailyQuestion(@RequestBody ScheduleRequest req) {
        if (req.problemId == null || req.activatesAt == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "problemId and activatesAt are required"));
        }

        // Verify the referenced problem actually exists
        if (!problemRepository.existsById(req.problemId)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Problem with ID '" + req.problemId + "' does not exist"));
        }

        Instant activatesAt = Instant.parse(req.activatesAt);
        Instant expiresAt = activatesAt.plus(Duration.ofHours(24));

        DailyQuestion dq = new DailyQuestion();
        dq.setProblemId(req.problemId);
        dq.setActivatesAt(activatesAt);
        dq.setExpiresAt(expiresAt);
        dq.setLabel(req.label != null ? req.label : "Daily Challenge");
        dq.setScheduledBy(req.scheduledBy != null ? req.scheduledBy : "Admin");

        DailyQuestion saved = dailyQuestionRepository.save(dq);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("message", "Daily question scheduled successfully");
        response.put("activatesAt", saved.getActivatesAt());
        response.put("expiresAt", saved.getExpiresAt());

        return ResponseEntity.ok(response);
    }

    /**
     * ADMIN endpoint — delete a scheduled daily question.
     */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<?> deleteDailyQuestion(@PathVariable String id) {
        if (!dailyQuestionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        dailyQuestionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Daily question entry deleted successfully"));
    }
}
