package com.aegis.assessment_service.service;

import com.aegis.assessment_service.dto.CandidateAssessmentDetails;
import com.aegis.assessment_service.dto.CandidateAssessmentSummary;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.entitiy.LeaderboardRecord;
import com.aegis.assessment_service.repository.AssessmentRegistrationRepository;
import com.aegis.assessment_service.repository.AssessmentRepository;
import com.aegis.assessment_service.repository.LeaderboardRecordRepository;
import com.aegis.assessment_service.util.AssessmentClock;
import com.aegis.assessment_service.util.AssessmentPolicy;
import com.aegis.judge.harness.StarterCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentRegistrationRepository registrationRepository;
    private final CompanyProblemService problemService;
    private final LeaderboardRecordRepository leaderboardRepository;

    // ─── COMPANY: CREATE ─────────────────────────────────────────

    @Transactional
    public Assessment createAssessment(String companyId, Assessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("Assessment payload is required.");
        }
        if (assessment.getTitle() == null || assessment.getTitle().isBlank()) {
            throw new IllegalArgumentException("Assessment title is required.");
        }
        if (assessment.getStartTime() == null || assessment.getEndTime() == null) {
            throw new IllegalArgumentException("Start time and end time are required.");
        }
        if (!assessment.getEndTime().isAfter(assessment.getStartTime())) {
            throw new IllegalArgumentException("End time must be after the start time.");
        }
        if (assessment.getDurationMinutes() <= 0) {
            throw new IllegalArgumentException("Duration must be greater than zero.");
        }
        long windowMinutes = Duration.between(assessment.getStartTime(), assessment.getEndTime()).toMinutes();
        if (assessment.getDurationMinutes() > windowMinutes) {
            throw new IllegalArgumentException("Duration cannot exceed the scheduled start-to-end window.");
        }
        LocalDateTime now = AssessmentClock.now();
        if (!assessment.getStartTime().isAfter(now)) {
            throw new IllegalArgumentException("Start time must be in the future so candidates can register.");
        }
        if (now.isAfter(assessment.getEndTime())) {
            throw new IllegalArgumentException("End time must be in the future.");
        }

        List<String> problemIds = assessment.getCompanyProblemIds();
        if (problemIds == null || problemIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one problem for the assessment.");
        }
        List<String> distinctIds = problemIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (distinctIds.size() != problemIds.size()) {
            throw new IllegalArgumentException("Problem list contains blank or duplicate entries.");
        }
        // Never trust client-supplied problem ids: every problem must belong to this company.
        distinctIds.forEach(problemId -> problemService.getOwnedProblem(companyId, problemId));

        assessment.setId(null);              // prevent upserting an existing document by id
        assessment.setCompanyId(companyId);  // never trust the client's companyId
        assessment.setCompanyProblemIds(new ArrayList<>(distinctIds));
        assessment.setStatus("UPCOMING");

        // Compact 8-char code: first 4 chars + "-" + next 2, e.g. "A7X9-B2".
        // Codes are the only secret used for late entry, so never reuse one.
        String testCode;
        do {
            String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase(Locale.ROOT);
            testCode = raw.substring(0, 4) + "-" + raw.substring(4);
        } while (assessmentRepository.existsByTestCode(testCode));
        assessment.setTestCode(testCode);
        return assessmentRepository.save(assessment);
    }

    // ─── CANDIDATE: JOIN ─────────────────────────────────────────

    @Transactional(noRollbackFor = SecurityException.class)
    public Assessment validateAndJoin(String testCode, String candidateId) {
        if (testCode == null || testCode.isBlank()) {
            throw new IllegalArgumentException("A test code is required.");
        }
        Assessment assessment = assessmentRepository.findByTestCode(testCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Invalid test code. Please check and try again."));

        AssessmentRegistration registration = registrationRepository
                .findByAssessmentIdAndCandidateId(assessment.getId(), candidateId)
                .orElseThrow(() -> new SecurityException(
                        "ACCESS DENIED: You must register for this tournament before joining."));

        LocalDateTime now = AssessmentClock.now();

        if (AssessmentPolicy.isFinal(registration.getStatus())) {
            throw new SecurityException("ACCESS DENIED: Your session is "
                    + registration.getStatus().toLowerCase(Locale.ROOT) + " and cannot be re-entered.");
        }
        if (now.isBefore(assessment.getStartTime())) {
            throw new IllegalStateException("The assessment has not started yet. Please wait.");
        }
        if (now.isAfter(assessment.getEndTime())) {
            throw new IllegalStateException("This assessment has already concluded.");
        }

        if (registration.getStartedAt() != null) {
            LocalDateTime deadline = AssessmentPolicy.deadline(assessment, registration);
            if (deadline != null && now.isAfter(deadline)) {
                registration.setStatus("COMPLETED");
                registrationRepository.save(registration);
                throw new SecurityException("Your assessment window has ended.");
            }
        }

        if (AssessmentPolicy.isRegistered(registration.getStatus())) {
            if (now.isAfter(assessment.getStartTime().plus(AssessmentPolicy.LATE_ENTRY_WINDOW))) {
                throw new SecurityException(
                        "ACCESS DENIED: The late entry window has closed. "
                                + "You must join within 10 minutes of the start time.");
            }
            registration.setStartedAt(now);
            registration.setStatus("IN_PROGRESS");
            registrationRepository.save(registration);
        } else if (!AssessmentPolicy.isInProgress(registration.getStatus())) {
            throw new SecurityException("ACCESS DENIED: This registration is not active.");
        }

        return assessment;
    }

    // ─── CANDIDATE: SAFE READS ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<CompanyProblem> getSanitizedProblemsForCandidate(String assessmentId, String candidateId) {
        Assessment assessment = requireAssessment(assessmentId);
        AssessmentRegistration registration = requireRegistration(assessmentId, candidateId);
        ensureWorkspaceOpen(assessment, registration);

        if (assessment.getCompanyProblemIds() == null || assessment.getCompanyProblemIds().isEmpty()) {
            return List.of();
        }

        List<CompanyProblem> problems = new ArrayList<>();
        for (String problemId : assessment.getCompanyProblemIds()) {
            try {
                CompanyProblem problem = problemService.getProblemById(problemId);
                if (Objects.equals(assessment.getCompanyId(), problem.getCompanyId())) {
                    problems.add(sanitizeProblem(problem));
                }
            } catch (IllegalArgumentException ex) {
                // A deleted problem should not make the whole workspace unusable.
                log.warn("Assessment {} references a missing problem: {}", assessmentId, problemId);
            }
        }
        return problems;
    }

    @Transactional(readOnly = true)
    public CandidateAssessmentDetails getAssessmentDetailsForCandidate(String assessmentId, String candidateId) {
        Assessment assessment = requireAssessment(assessmentId);
        AssessmentRegistration registration = requireRegistration(assessmentId, candidateId);
        return toDetails(assessment, registration, AssessmentClock.now());
    }

    @Transactional(readOnly = true)
    public List<CandidateAssessmentDetails> getCandidateTournaments(String candidateId) {
        List<String> assessmentIds = registrationRepository.findByCandidateId(candidateId).stream()
                .map(AssessmentRegistration::getAssessmentId)
                .toList();
        if (assessmentIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = AssessmentClock.now();
        return assessmentRepository.findAllById(assessmentIds).stream()
                .map(assessment -> {
                    AssessmentRegistration registration = registrationRepository
                            .findByAssessmentIdAndCandidateId(assessment.getId(), candidateId)
                            .orElse(null);
                    return registration == null ? null : toDetails(assessment, registration, now);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardRecord> getLeaderboardIfAvailable(String assessmentId, String candidateId) {
        Assessment assessment = requireAssessment(assessmentId);
        requireRegistration(assessmentId, candidateId);

        // Leaderboard is hidden until the global assessment window ends
        if (assessment.getEndTime() != null && AssessmentClock.now().isBefore(assessment.getEndTime())) {
            throw new SecurityException("The leaderboard is hidden until the assessment concludes.");
        }

        return leaderboardRepository
                .findByAssessmentIdOrderByTotalPointsDescTotalTimePenaltySecondsAsc(assessmentId);
    }

    @Transactional(readOnly = true)
    public Page<CandidateAssessmentSummary> getAvailableAssessments(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        LocalDateTime now = AssessmentClock.now();

        Page<Assessment> assessments;
        if (search == null || search.isBlank()) {
            assessments = assessmentRepository.findByEndTimeAfterOrderByStartTimeAsc(now, pageable);
        } else {
            assessments = assessmentRepository
                    .findByTitleContainingIgnoreCaseAndEndTimeAfterOrderByStartTimeAsc(search.trim(), now, pageable);
        }
        return assessments.map(assessment -> toSummary(assessment, now));
    }

    // ─── COMPANY: READ ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Assessment> getAssessmentsByCompany(String companyId) {
        return assessmentRepository.findByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public Assessment getAssessmentById(String assessmentId) {
        return requireAssessment(assessmentId);
    }

    // ─── MAPPERS / GUARDS ────────────────────────────────────────

    public CandidateAssessmentSummary toSummary(Assessment assessment) {
        return toSummary(assessment, AssessmentClock.now());
    }

    private CandidateAssessmentSummary toSummary(Assessment assessment, LocalDateTime now) {
        return new CandidateAssessmentSummary(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getStartTime(),
                assessment.getEndTime(),
                assessment.getDurationMinutes(),
                AssessmentPolicy.phase(assessment, now),
                assessment.getCompanyProblemIds() == null ? 0 : assessment.getCompanyProblemIds().size()
        );
    }

    private CandidateAssessmentDetails toDetails(Assessment assessment, AssessmentRegistration registration, LocalDateTime now) {
        return new CandidateAssessmentDetails(
                assessment.getId(),
                assessment.getTitle(),
                assessment.getDescription(),
                assessment.getStartTime(),
                assessment.getEndTime(),
                assessment.getDurationMinutes(),
                AssessmentPolicy.phase(assessment, now),
                assessment.getCompanyProblemIds() == null ? 0 : assessment.getCompanyProblemIds().size(),
                registration.getStatus(),
                registration.getRegisteredAt(),
                registration.getStartedAt(),
                AssessmentPolicy.deadline(assessment, registration),
                AssessmentPolicy.canEnter(assessment, registration, now)
        );
    }

    private Assessment requireAssessment(String assessmentId) {
        return assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));
    }

    private AssessmentRegistration requireRegistration(String assessmentId, String candidateId) {
        return registrationRepository.findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseThrow(() -> new SecurityException("You are not registered for this assessment."));
    }

    /**
     * A candidate may only read problems while their personal workspace is open.
     * Re-entering an IN_PROGRESS session is allowed until the deadline.
     */
    private void ensureWorkspaceOpen(Assessment assessment, AssessmentRegistration registration) {
        LocalDateTime now = AssessmentClock.now();
        if (AssessmentPolicy.isFinal(registration.getStatus())) {
            throw new SecurityException("ACCESS DENIED: This session is "
                    + registration.getStatus().toLowerCase(Locale.ROOT) + " and cannot be resumed.");
        }
        if (!AssessmentPolicy.isInProgress(registration.getStatus()) || registration.getStartedAt() == null) {
            throw new SecurityException("ACCESS DENIED: Start the assessment before requesting problems.");
        }
        if (now.isBefore(assessment.getStartTime())) {
            throw new SecurityException("ACCESS DENIED: This assessment has not started yet.");
        }
        if (assessment.getEndTime() != null && now.isAfter(assessment.getEndTime())) {
            throw new SecurityException("ACCESS DENIED: This assessment has already concluded.");
        }
        LocalDateTime deadline = AssessmentPolicy.deadline(assessment, registration);
        if (deadline != null && now.isAfter(deadline)) {
            throw new SecurityException("ACCESS DENIED: Your assessment window has ended.");
        }
    }

    /**
     * Returns a detached copy so the persistence context never exposes hidden
     * test cases or company ownership to the candidate response.
     */
    private CompanyProblem sanitizeProblem(CompanyProblem problem) {
        CompanyProblem safe = new CompanyProblem();
        safe.setId(problem.getId());
        safe.setTitle(problem.getTitle());
        safe.setDifficulty(problem.getDifficulty());
        safe.setDescription(problem.getDescription());
        safe.setStartingCodeJava(problem.getStartingCodeJava());
        safe.setStartingCode(StarterCode.forProblem(
                problem.getStartingCode(),
                problem.getStartingCodeJava(),
                problem.getMethodName(),
                problem.getReturnType(),
                problem.getParameterTypes()));
        safe.setTimeLimitMs(problem.getTimeLimitMs());
        safe.setMemoryLimitKb(problem.getMemoryLimitKb());
        safe.setMethodName(problem.getMethodName());
        safe.setReturnType(problem.getReturnType());
        safe.setParameterTypes(problem.getParameterTypes());

        List<CompanyProblem.TestCase> publicCases = problem.getTestCases() == null
                ? List.of()
                : problem.getTestCases().stream()
                        .filter(tc -> tc != null && !tc.isHidden())
                        .map(tc -> {
                            CompanyProblem.TestCase copy = new CompanyProblem.TestCase();
                            copy.setInputs(tc.getInputs());
                            copy.setExpectedOutput(tc.getExpectedOutput());
                            copy.setHidden(false);
                            return copy;
                        })
                        .collect(Collectors.toList());
        safe.setTestCases(publicCases);
        return safe;
    }
}
