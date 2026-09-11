package com.aegis.assessment_service.service;

import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.entitiy.LeaderboardRecord;
import com.aegis.assessment_service.repository.AssessmentRepository;
import com.aegis.assessment_service.repository.AssessmentRegistrationRepository;
import com.aegis.assessment_service.repository.LeaderboardRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentRegistrationRepository registrationRepository;
    private final CompanyProblemService problemService;
    private final LeaderboardRecordRepository leaderboardRepository;

    /**
     * Always use nowUtc() instead of LocalDateTime.now().
     *
     * WHY: LocalDateTime.now() uses the JVM's default timezone.
     * If the server is deployed in UTC (most cloud hosts) and the frontend
     * appends 'Z' to treat stored times as UTC, everything lines up.
     * But if the JVM timezone drifts (e.g., someone sets it to Asia/Kolkata),
     * all comparisons silently break by 5h30m.
     *
     * Pinning to ZoneOffset.UTC makes this explicit and server-location-agnostic.
     */
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private static LocalDateTime nowIst() {
        return LocalDateTime.now(IST);
    }

    @Transactional
    public Assessment createAssessment(String companyId, Assessment assessment) {
        assessment.setCompanyId(companyId);

        // Compact 8-char code: first 4 chars + "-" + next 2, e.g. "A7X9-B2"
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        assessment.setTestCode(raw.substring(0, 4) + "-" + raw.substring(4));

        assessment.setStatus("UPCOMING");
        return assessmentRepository.save(assessment);
    }

    @Transactional
    public Assessment validateAndJoin(String testCode, String candidateId) {
        Assessment assessment = assessmentRepository.findByTestCode(testCode)
                .orElseThrow(() -> new RuntimeException("Invalid test code. Please check and try again."));

        LocalDateTime now = nowIst();

        // 1. Too early
        if (now.isBefore(assessment.getStartTime())) {
            throw new RuntimeException("The assessment has not started yet. Please wait.");
        }

        // 2. Late entry window closed (10 minutes after start)
        if (now.isAfter(assessment.getStartTime().plusMinutes(10))) {
            throw new RuntimeException(
                    "ACCESS DENIED: The late entry window has closed. " +
                            "You must join within 10 minutes of the start time."
            );
        }

        // 3. Tournament over
        if (now.isAfter(assessment.getEndTime())) {
            throw new RuntimeException("This assessment has already concluded.");
        }

        // 4. Must be formally registered
        // FIX: Use a targeted repository query instead of fetching ALL registrations
        // and filtering in Java (N+1 risk). Add findByAssessmentIdAndCandidateId to your repo.
        AssessmentRegistration registration = registrationRepository
                .findByAssessmentIdAndCandidateId(assessment.getId(), candidateId)
                .orElseThrow(() -> new RuntimeException(
                        "ACCESS DENIED: You must register for this tournament before joining."
                ));

        // 5. Locked accounts are blocked at the gate
        if ("COMPLETED".equals(registration.getStatus()) || "TERMINATED".equals(registration.getStatus())) {
            throw new RuntimeException(
                    "ACCESS DENIED: Your session for this assessment is " +
                            registration.getStatus().toLowerCase() + " and cannot be re-entered."
            );
        }

        // 6. First-time entry → transition to IN_PROGRESS
        if ("REGISTERED".equals(registration.getStatus())) {
            registration.setStatus("IN_PROGRESS");
            registrationRepository.save(registration);
        }

        return assessment;
    }

    // ─── CANDIDATE HELPERS ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CompanyProblem> getSanitizedProblemsForCandidate(String assessmentId, String candidateId) {
        if (!registrationRepository.existsByAssessmentIdAndCandidateId(assessmentId, candidateId)) {
            throw new SecurityException("You are not registered for this assessment.");
        }

        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));

        return assessment.getCompanyProblemIds().stream()
                .map(problemService::getProblemById)
                .peek(problem -> {
                    // Strip hidden test cases before sending to client
                    List<CompanyProblem.TestCase> publicCases = problem.getTestCases().stream()
                            .filter(tc -> !tc.isHidden())
                            .collect(Collectors.toList());
                    problem.setTestCases(publicCases);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardRecord> getLeaderboardIfAvailable(String assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));

        // Leaderboard is hidden until the assessment ends
        if (nowIst().isBefore(assessment.getEndTime())) {
            throw new SecurityException("The leaderboard is hidden until the assessment concludes.");
        }

        return leaderboardRepository
                .findByAssessmentIdOrderByTotalPointsDescTotalTimePenaltySecondsAsc(assessmentId);
    }

    @Transactional(readOnly = true)
    public Page<Assessment> getAvailableAssessments(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime now = nowIst();

        if (search == null || search.isBlank()) {
            return assessmentRepository.findByEndTimeAfterOrderByStartTimeAsc(now, pageable);
        }
        return assessmentRepository
                .findByTitleContainingIgnoreCaseAndEndTimeAfterOrderByStartTimeAsc(search, now, pageable);
    }

    @Transactional(readOnly = true)
    public List<Assessment> getAssessmentsByCompany(String companyId) {
        return assessmentRepository.findByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public Assessment getAssessmentById(String assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with ID: " + assessmentId));
    }
}