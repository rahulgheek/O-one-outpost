package com.aegis.assessment_service.controller;

import com.aegis.assessment_service.dto.CandidateAssessmentSummary;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/candidate")
@RequiredArgsConstructor
public class CandidateJoinController {

    private final AssessmentService assessmentService;

    @PostMapping("/join")
    public ResponseEntity<?> joinAssessment(
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "loggedInUser", required = false) String candidateId) {

        // Defence in depth: the gateway normally injects this header, but a direct
        // service call must never be allowed through without an identity.
        if (candidateId == null || candidateId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: Gateway did not pass user data.");
        }

        try {
            Assessment assessment = assessmentService.validateAndJoin(
                    payload == null ? null : payload.get("testCode"), candidateId);
            CandidateAssessmentSummary summary = assessmentService.toSummary(assessment);
            return ResponseEntity.ok(summary);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
