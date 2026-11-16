package com.aegis.assessment_service.util;

import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Shared assessment rules so candidate, company, and execution flows cannot
 * disagree about timing or lifecycle state.
 */
public final class AssessmentPolicy {

    /** Candidates may enter with an invite code during this window after start. */
    public static final Duration LATE_ENTRY_WINDOW = Duration.ofMinutes(10);

    private AssessmentPolicy() {
    }

    /**
     * Per-candidate deadline: the smaller of the candidate's own duration and
     * the company's global cutoff. The personal clock starts at {@code startedAt}.
     */
    public static LocalDateTime deadline(Assessment assessment, AssessmentRegistration registration) {
        if (assessment == null || assessment.getEndTime() == null) {
            return null;
        }
        LocalDateTime start = registration != null && registration.getStartedAt() != null
                ? registration.getStartedAt()
                : assessment.getStartTime();
        if (start == null || assessment.getDurationMinutes() <= 0) {
            return assessment.getEndTime();
        }
        LocalDateTime personalDeadline = start.plusMinutes(assessment.getDurationMinutes());
        return personalDeadline.isBefore(assessment.getEndTime())
                ? personalDeadline
                : assessment.getEndTime();
    }

    /** Derived lifecycle phase. The stored {@code status} field is not authoritative. */
    public static String phase(Assessment assessment, LocalDateTime now) {
        if (assessment == null || assessment.getStartTime() == null) {
            return "UPCOMING";
        }
        if (now.isBefore(assessment.getStartTime())) {
            return "UPCOMING";
        }
        if (assessment.getEndTime() != null && now.isAfter(assessment.getEndTime())) {
            return "ENDED";
        }
        return "ONGOING";
    }

    public static boolean isFinal(String status) {
        return "COMPLETED".equals(status) || "TERMINATED".equals(status);
    }

    public static boolean isRegistered(String status) {
        return "REGISTERED".equals(status);
    }

    public static boolean isInProgress(String status) {
        return "IN_PROGRESS".equals(status);
    }

    /**
     * Whether the candidate currently has a valid path into the workspace.
     * Used by the dashboard to render a real "Enter arena" affordance.
     */
    public static boolean canEnter(Assessment assessment, AssessmentRegistration registration, LocalDateTime now) {
        if (assessment == null || registration == null || isFinal(registration.getStatus())) {
            return false;
        }
        if (assessment.getStartTime() == null
                || now.isBefore(assessment.getStartTime())
                || (assessment.getEndTime() != null && now.isAfter(assessment.getEndTime()))) {
            return false;
        }
        LocalDateTime deadline = deadline(assessment, registration);
        if (deadline != null && now.isAfter(deadline)) {
            return false;
        }
        if (isRegistered(registration.getStatus())) {
            return !now.isAfter(assessment.getStartTime().plus(LATE_ENTRY_WINDOW));
        }
        return isInProgress(registration.getStatus());
    }

    public static String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "MEDIUM";
        }
        return difficulty.trim().toUpperCase(Locale.ROOT);
    }
}
