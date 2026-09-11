package com.aegis.assessment_service.controller;

import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.service.AssessmentService;
import lombok.RequiredArgsConstructor;
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
            // I added 'required = false' so Spring doesn't auto-block it before we can log it!
            @RequestHeader(value = "loggedInUser", required = false) String candidateId) {

        // 1. Check if Gateway is failing to pass the user
        if (candidateId == null) {
            System.err.println("🚨 [GATEWAY ERROR] Missing 'loggedInUser' header! Is this route secured in your Gateway?");
            return ResponseEntity.status(401).body("Unauthorized: Gateway did not pass user data.");
        }

        try {
            String testCode = payload.get("testCode");
            System.out.println("🟢 [JOIN REQUEST] User '" + candidateId + "' attempting to join with code: " + testCode);

            Assessment assessment = assessmentService.validateAndJoin(testCode, candidateId);

            System.out.println("✅ [JOIN SUCCESS] User '" + candidateId + "' entered the arena.");
            return ResponseEntity.ok(assessment);

        } catch (RuntimeException e) {
            // 2. NOW WE LOG THE ACTUAL ERROR!
            System.err.println("❌ [JOIN REJECTED] " + e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}