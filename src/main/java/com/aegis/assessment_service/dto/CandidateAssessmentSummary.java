package com.aegis.assessment_service.dto;

import java.time.LocalDateTime;

/**
 * Candidate-safe assessment view. Deliberately excludes the invite code,
 * company ownership, and the internal problem id list.
 */
public record CandidateAssessmentSummary(
        String id,
        String title,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int durationMinutes,
        String phase,
        int problemCount
) {
}
