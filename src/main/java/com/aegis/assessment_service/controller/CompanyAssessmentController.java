package com.aegis.assessment_service.controller;

import com.aegis.assessment_service.dto.CandidatePerformanceView;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.service.AssessmentRegistrationService;
import com.aegis.assessment_service.service.AssessmentService;
import com.aegis.assessment_service.service.CompanyProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assessment/company")
@RequiredArgsConstructor
public class CompanyAssessmentController {

    private final CompanyProblemService problemService;
    private final AssessmentService assessmentService;
    private final AssessmentRegistrationService registrationService;

    // ==========================================
    // 1. THE PRIVATE QUESTION BANK API
    // ==========================================

    @PostMapping("/problems")
    public ResponseEntity<?> createPrivateProblem(
            @RequestHeader("loggedInUser") String companyId,
            @RequestBody CompanyProblem problem) {
        try {
            return ResponseEntity.ok(problemService.createPrivateProblem(companyId, problem));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/problems")
    public ResponseEntity<List<CompanyProblem>> getCompanyProblems(
            @RequestHeader("loggedInUser") String companyId) {
        return ResponseEntity.ok(problemService.getProblemsByCompany(companyId));
    }

    @GetMapping("/problems/{problemId}")
    public ResponseEntity<?> getSingleProblem(
            @RequestHeader("loggedInUser") String companyId,
            @PathVariable String problemId) {
        try {
            return ResponseEntity.ok(problemService.getOwnedProblem(companyId, problemId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // ==========================================
    // 2. THE TOURNAMENT GENERATOR API
    // ==========================================

    @PostMapping("/create")
    public ResponseEntity<?> createAssessment(
            @RequestHeader("loggedInUser") String companyId,
            @RequestBody Assessment assessment) {
        try {
            Assessment createdAssessment = assessmentService.createAssessment(companyId, assessment);
            return ResponseEntity.ok(createdAssessment);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/assessments")
    public ResponseEntity<List<Assessment>> getCompanyAssessments(
            @RequestHeader("loggedInUser") String companyId) {
        return ResponseEntity.ok(assessmentService.getAssessmentsByCompany(companyId));
    }

    @GetMapping("/{assessmentId}/candidates")
    public ResponseEntity<?> getTournamentCandidates(
            @RequestHeader("loggedInUser") String companyId,
            @PathVariable String assessmentId) {
        try {
            List<AssessmentRegistration> candidates =
                    registrationService.getParticipantsForTournament(companyId, assessmentId);
            return ResponseEntity.ok(candidates);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * Selection table data: registrations joined with leaderboard totals.
     * Accessible to the owning company only.
     */
    @GetMapping("/{assessmentId}/results")
    public ResponseEntity<?> getCandidateResults(
            @RequestHeader("loggedInUser") String companyId,
            @PathVariable String assessmentId) {
        try {
            List<CandidatePerformanceView> results =
                    registrationService.getCandidatePerformance(companyId, assessmentId);
            return ResponseEntity.ok(results);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
