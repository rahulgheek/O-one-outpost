package com.aegis.assessment_service.dto;

/**
 * Submission response that never echoes the submitted source back to the client.
 */
public record SubmissionResult(
        String id,
        String executionStatus,
        int pointsAwarded,
        int testCasesPassed,
        int totalTestCases
) {
}
