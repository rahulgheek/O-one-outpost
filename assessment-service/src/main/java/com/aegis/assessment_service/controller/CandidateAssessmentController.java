package com.aegis.assessment_service.controller;

import com.aegis.assessment_service.dto.CandidateAssessmentDetails;
import com.aegis.assessment_service.dto.CandidateAssessmentSummary;
import com.aegis.assessment_service.dto.RegistrationRequest;
import com.aegis.assessment_service.dto.SubmissionResult;
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
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId) {
        try {
            CandidateAssessmentDetails details =
                    assessmentService.getAssessmentDetailsForCandidate(assessmentId, candidateId);
            return ResponseEntity.ok(details);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/{assessmentId}/problems")
    public ResponseEntity<?> getAssessmentProblems(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId) {
        try {
            List<CompanyProblem> problems =
                    assessmentService.getSanitizedProblemsForCandidate(assessmentId, candidateId);
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

        String rawCode = payload == null ? null : payload.get("code");
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Code cannot be empty.");
        }

        try {
            SubmissionResult result = executionService.submitCode(assessmentId, candidateId, problemId, rawCode);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{assessmentId}/leaderboard")
    public ResponseEntity<?> getFinalLeaderboard(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId) {
        try {
            List<LeaderboardRecord> leaderboard =
                    assessmentService.getLeaderboardIfAvailable(assessmentId, candidateId);
            return ResponseEntity.ok(leaderboard);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/directory")
    public ResponseEntity<Page<CandidateAssessmentSummary>> browseAssessments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "") String search) {

        return ResponseEntity.ok(assessmentService.getAvailableAssessments(search, page, size));
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
    public ResponseEntity<List<CandidateAssessmentDetails>> getMyUpcomingTournaments(
            @RequestHeader("loggedInUser") String candidateId) {

        return ResponseEntity.ok(assessmentService.getCandidateTournaments(candidateId));
    }

    @PostMapping("/{assessmentId}/start")
    public ResponseEntity<String> startWorkspace(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId) {
        try {
            registrationService.startWorkspaceTimer(assessmentId, candidateId);
            return ResponseEntity.ok("Workspace initialized. Clock is ticking!");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/{assessmentId}/finish")
    public ResponseEntity<String> finishAssessment(
            @RequestHeader("loggedInUser") String candidateId,
            @PathVariable String assessmentId,
            @RequestParam String reason) {
        try {
            registrationService.lockAssessment(assessmentId, candidateId, reason);
            return ResponseEntity.ok("Assessment locked with status: " + reason);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
