package com.aegis.assessment_service.controller;

import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.service.AssessmentRegistrationService;
import com.aegis.assessment_service.service.AssessmentService;
import com.aegis.assessment_service.service.CompanyProblemService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<CompanyProblem> createPrivateProblem(
            @RequestHeader("loggedInUser") String companyId,
            @RequestBody CompanyProblem problem) {

        CompanyProblem savedProblem = problemService.createPrivateProblem(companyId, problem);
        return ResponseEntity.ok(savedProblem);
    }

    @GetMapping("/problems")
    public ResponseEntity<List<CompanyProblem>> getCompanyProblems(
            @RequestHeader("loggedInUser") String companyId) {

        List<CompanyProblem> problems = problemService.getProblemsByCompany(companyId);
        return ResponseEntity.ok(problems);
    }

    @GetMapping("/problems/{problemId}")
    public ResponseEntity<CompanyProblem> getSingleProblem(
            @RequestHeader("loggedInUser") String companyId,
            @PathVariable String problemId) {

        CompanyProblem problem = problemService.getProblemById(problemId);

        // Security check: Make sure they aren't looking at another company's problem!
        if (!problem.getCompanyId().equals(companyId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(problem);
    }

    // ==========================================
    // 2. THE TOURNAMENT GENERATOR API
    // ==========================================

    @PostMapping("/create")
    public ResponseEntity<Assessment> createAssessment(
            @RequestHeader("loggedInUser") String companyId,
            @RequestBody Assessment assessment) {

        // The service automatically generates the secure testCode
        Assessment createdAssessment = assessmentService.createAssessment(companyId, assessment);
        return ResponseEntity.ok(createdAssessment);
    }

    @GetMapping("/assessments")
    public ResponseEntity<List<Assessment>> getCompanyAssessments(
            @RequestHeader("loggedInUser") String companyId) {
        
        return ResponseEntity.ok(assessmentService.getAssessmentsByCompany(companyId));
    }

    @GetMapping("/{assessmentId}/candidates")
    public ResponseEntity<List<AssessmentRegistration>> getTournamentCandidates(
            @RequestHeader("loggedInUser") String companyId,
            @PathVariable String assessmentId) {

        return ResponseEntity.ok(registrationService.getParticipantsForTournament(companyId, assessmentId));
    }
}