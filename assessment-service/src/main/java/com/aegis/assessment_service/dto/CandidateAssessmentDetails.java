package com.aegis.assessment_service.dto;

import java.time.LocalDateTime;

/**
 * Candidate-safe detail view enriched with registration state and the
 * candidate's personal deadline.
 */
public record CandidateAssessmentDetails(
        String id,
        String title,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int durationMinutes,
        String phase,
        int problemCount,
        String registrationStatus,
        LocalDateTime registeredAt,
        LocalDateTime startedAt,
        LocalDateTime deadline,
        boolean canEnter
) {
}
