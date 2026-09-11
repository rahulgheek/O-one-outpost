package com.aegis.assessment_service.controller;

import com.aegis.assessment_service.dto.RegistrationRequest;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.CandidateSubmission;
import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.entitiy.LeaderboardRecord;
import com.aegis.assessment_service.service.AssessmentExecutionService;
import com.aegis.assessment_service.service.AssessmentRegistrationService;
import com.aegis.assessment_service.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/candidate/workspace")
@RequiredArgsConstructor
public class CandidateAssessmentController {

    private final AssessmentService assessmentService;
    private final AssessmentExecutionService executionService;
    private final AssessmentRegistrationService registrationService;

    @GetMapping("/{assessmentId}")
    public ResponseEntity<?> getAssessmentDetails(
            @RequestHeader(value = "loggedInUser", required = false) String candidateId,
            @PathVariable String assessmentId) {
        try {
            Assessment assessment = assessmentService.getAssessmentById(assessmentId);
            return ResponseEntity.ok(assessment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/{assessmentId}/problems")
    public ResponseEntity<?> getAssessmentProblems(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId) {
        try {
            List<CompanyProblem> problems = assessmentService.getSanitizedProblemsForCandidate(assessmentId, candidateId);
            return ResponseEntity.ok(problems);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/{assessmentId}/problem/{problemId}/submit")
    public ResponseEntity<?> submitSolution(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId,
            @PathVariable String problemId,
            @RequestBody Map<String, String> payload) {

        String rawCode = payload.get("code");
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Code cannot be empty.");
        }

        try {
            CandidateSubmission result = executionService.submitCode(assessmentId, candidateId, problemId, rawCode);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    @GetMapping("/{assessmentId}/leaderboard")
    public ResponseEntity<?> getFinalLeaderboard(
            @PathVariable String assessmentId) {
        try {
            List<LeaderboardRecord> leaderboard = assessmentService.getLeaderboardIfAvailable(assessmentId);
            return ResponseEntity.ok(leaderboard);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/directory")
    public ResponseEntity<Page<Assessment>> browseAssessments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "") String search) {

        Page<Assessment> assessments = assessmentService.getAvailableAssessments(search, page, size);
        return ResponseEntity.ok(assessments);
    }

    @PostMapping("/{assessmentId}/register")
    public ResponseEntity<String> registerForTournament(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId,
            @RequestBody RegistrationRequest request) {

        try {
            registrationService.registerCandidate(assessmentId, candidateId, request);
            return ResponseEntity.ok("Successfully registered!");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my-tournaments")
    public ResponseEntity<List<Assessment>> getMyUpcomingTournaments(
            @RequestHeader("loggedInUser") String candidateId) {

        List<Assessment> activeTournaments = registrationService.getCandidateActiveTournaments(candidateId);
        return ResponseEntity.ok(activeTournaments);
    }

    @PostMapping("/{assessmentId}/start")
    public ResponseEntity<String> startWorkspace(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId) {
        try {
            registrationService.startWorkspaceTimer(assessmentId, candidateId);
            return ResponseEntity.ok("Workspace initialized. Clock is ticking!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 🚨 NEW ENDPOINT: Permanently locks the test
    @PostMapping("/{assessmentId}/finish")
    public ResponseEntity<String> finishAssessment(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId,
            @RequestParam String reason) {

        registrationService.lockAssessment(assessmentId, candidateId, reason);
        return ResponseEntity.ok("Assessment locked with status: " + reason);
    }
}