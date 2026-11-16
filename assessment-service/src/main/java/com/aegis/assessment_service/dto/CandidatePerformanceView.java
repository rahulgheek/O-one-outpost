package com.aegis.assessment_service.dto;

import java.time.LocalDateTime;

/**
 * Company-facing selection row: registration identity plus leaderboard totals.
 * Previously the UI expected score fields directly on AssessmentRegistration,
 * which does not contain them.
 */
public record CandidatePerformanceView(
        String registrationId,
        String candidateId,
        String fullName,
        String phoneNumber,
        String college,
        String degree,
        String status,
        LocalDateTime registeredAt,
        LocalDateTime startedAt,
        int totalPoints,
        long totalTimePenaltySeconds
) {
}
