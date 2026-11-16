package com.aegis.assessment_service.service;

import com.aegis.assessment_service.dto.SandboxRequest;
import com.aegis.assessment_service.dto.SubmissionResult;
import com.aegis.assessment_service.entitiy.Assessment;
import com.aegis.assessment_service.entitiy.AssessmentRegistration;
import com.aegis.assessment_service.entitiy.CandidateSubmission;
import com.aegis.assessment_service.entitiy.CompanyProblem;
import com.aegis.assessment_service.entitiy.LeaderboardRecord;
import com.aegis.assessment_service.repository.AssessmentRegistrationRepository;
import com.aegis.assessment_service.repository.AssessmentRepository;
import com.aegis.assessment_service.repository.CandidateSubmissionRepository;
import com.aegis.assessment_service.repository.LeaderboardRecordRepository;
import com.aegis.assessment_service.util.AssessmentClock;
import com.aegis.assessment_service.util.AssessmentPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentExecutionService {

    private static final int MAX_CODE_LENGTH = 200_000;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentRegistrationRepository registrationRepository;
    private final CompanyProblemService problemService;
    private final CandidateSubmissionRepository submissionRepository;
    private final LeaderboardRecordRepository leaderboardRepository;
    private final RestTemplate restTemplate;
    private final AssessmentCodeGeneratorService codeGeneratorService;

    @Value("${sandbox.url}")
    private String sandboxUrl;

    @Transactional(noRollbackFor = SecurityException.class)
    public SubmissionResult submitCode(String assessmentId, String candidateId, String problemId, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Code cannot be empty.");
        }
        if (rawCode.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Submitted code exceeds the maximum allowed size.");
        }

        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));

        AssessmentRegistration registration = registrationRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseThrow(() -> new SecurityException("You are not registered for this assessment."));

        if (AssessmentPolicy.isFinal(registration.getStatus())) {
            throw new SecurityException("ACCESS DENIED: This session is "
                    + registration.getStatus().toLowerCase(Locale.ROOT) + " and cannot accept submissions.");
        }
        if (!AssessmentPolicy.isInProgress(registration.getStatus()) || registration.getStartedAt() == null) {
            throw new SecurityException("ACCESS DENIED: Start the assessment before submitting.");
        }

        LocalDateTime now = AssessmentClock.now();
        if (assessment.getStartTime() == null || now.isBefore(assessment.getStartTime())) {
            throw new SecurityException("ACCESS DENIED: This assessment has not started yet.");
        }
        LocalDateTime deadline = AssessmentPolicy.deadline(assessment, registration);
        if (deadline != null && now.isAfter(deadline)) {
            registration.setStatus("COMPLETED");
            registrationRepository.save(registration);
            throw new SecurityException("Time limit exceeded. Submissions are closed.");
        }

        // The submitted problem must be explicitly part of this assessment.
        if (assessment.getCompanyProblemIds() == null
                || !assessment.getCompanyProblemIds().contains(problemId)) {
            throw new SecurityException("This problem does not belong to the assessment.");
        }

        CompanyProblem problem = problemService.getProblemById(problemId);
        if (!assessment.getCompanyId().equals(problem.getCompanyId())) {
            throw new SecurityException("This problem does not belong to the assessment.");
        }
        if (problem.getTestCases() == null || problem.getTestCases().isEmpty()) {
            throw new IllegalStateException("This problem has no test cases configured. Contact the recruiter.");
        }
        int totalTests = problem.getTestCases().size();

        int previousMaxPoints = submissionRepository
                .findByAssessmentIdAndCandidateIdAndProblemId(assessmentId, candidateId, problemId)
                .stream()
                .mapToInt(CandidateSubmission::getPointsAwarded)
                .max()
                .orElse(0);

        // ── Execute in the isolated sandbox ─────────────────────
        String executablePayload = codeGeneratorService.generateExecutableCode(rawCode, problem);
        SandboxRequest request = new SandboxRequest(executablePayload, problem.getTimeLimitMs(), problem.getMemoryLimitKb());

        String sandboxOutput;
        try {
            sandboxOutput = restTemplate.postForObject(sandboxUrl, request, String.class);
        } catch (Exception e) {
            log.error("Sandbox invocation failed for assessment {}", assessmentId, e);
            throw new IllegalStateException("SYSTEM_ERROR|Failed to reach Code Sandbox");
        }

        int passedTests;
        int pointsAwarded;
        String executionStatus;

        if (sandboxOutput == null || sandboxOutput.isBlank()) {
            throw new IllegalStateException("Execution service returned no result. Please retry.");
        }
        if (sandboxOutput.startsWith("SYSTEM_ERROR")) {
            log.error("Sandbox system error for assessment {}: {}", assessmentId, sandboxOutput);
            throw new IllegalStateException("Execution service is temporarily unavailable. Please retry.");
        }

        if (sandboxOutput.contains("COMPILATION_ERROR")) {
            passedTests = 0;
            pointsAwarded = 0;
            executionStatus = sandboxOutput
                    .replace("/app/Main.java:", "Error at Line ")
                    .replace("COMPILATION_ERROR", "COMPILATION FAILED:\n");
        } else if (sandboxOutput.startsWith("TIME_LIMIT_EXCEEDED")) {
            passedTests = 0;
            pointsAwarded = 0;
            executionStatus = "TIME_LIMIT_EXCEEDED";
        } else if (sandboxOutput.startsWith("MEMORY_LIMIT_EXCEEDED")) {
            passedTests = 0;
            pointsAwarded = 0;
            executionStatus = "MEMORY_LIMIT_EXCEEDED";
        } else if (sandboxOutput.startsWith("RUNTIME_ERROR")) {
            passedTests = 0;
            pointsAwarded = 0;
            executionStatus = "RUNTIME_ERROR";
        } else {
            passedTests = parseSandboxPassCount(sandboxOutput, totalTests);
            int weight = getDifficultyWeight(problem.getDifficulty());
            pointsAwarded = Math.min(weight, Math.max(0, (int) (((double) passedTests / totalTests) * weight)));

            if (passedTests == totalTests) {
                executionStatus = "ALL_PASSED";
            } else if (passedTests > 0) {
                executionStatus = "PARTIAL_SUCCESS";
            } else {
                executionStatus = "FAILED (Check logic or runtime errors)";
            }
        }

        CandidateSubmission submission = new CandidateSubmission();
        submission.setAssessmentId(assessmentId);
        submission.setCandidateId(candidateId);
        submission.setProblemId(problemId);
        submission.setSubmittedCode(rawCode);
        submission.setTestCasesPassed(passedTests);
        submission.setTotalTestCases(totalTests);
        submission.setPointsAwarded(pointsAwarded);
        submission.setSubmissionTime(now);
        submission.setExecutionStatus(executionStatus);
        CandidateSubmission saved = submissionRepository.save(submission);

        if (pointsAwarded > previousMaxPoints) {
            long elapsedSeconds = Math.max(0L, Duration.between(registration.getStartedAt(), now).getSeconds());
            recalculateLeaderboard(assessmentId, candidateId, elapsedSeconds);
        }

        return new SubmissionResult(
                saved.getId(),
                executionStatus,
                pointsAwarded,
                passedTests,
                totalTests
        );
    }

    /**
     * Recomputes total points from every submission so the leaderboard cannot
     * drift when a candidate improves the same problem multiple times.
     * The tie-break penalty is the candidate's elapsed time at their latest
     * scoring submission, measured from their own start (not the global start).
     */
    private void recalculateLeaderboard(String assessmentId, String candidateId, long elapsedSeconds) {
        List<CandidateSubmission> submissions = submissionRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId);

        int totalPoints = submissions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CandidateSubmission::getProblemId,
                        java.util.stream.Collectors.maxBy(
                                java.util.Comparator.comparingInt(CandidateSubmission::getPointsAwarded))))
                .values().stream()
                .filter(java.util.Optional::isPresent)
                .mapToInt(optional -> optional.get().getPointsAwarded())
                .sum();

        LeaderboardRecord record = leaderboardRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElseGet(() -> new LeaderboardRecord(null, assessmentId, candidateId, 0, 0L));

        record.setTotalPoints(totalPoints);
        record.setTotalTimePenaltySeconds(elapsedSeconds);
        leaderboardRepository.save(record);
    }

    private int getDifficultyWeight(String difficulty) {
        String normalized = difficulty == null ? "" : difficulty.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EASY" -> 100;
            case "MEDIUM" -> 300;
            case "HARD" -> 500;
            default -> 100;
        };
    }

    private int parseSandboxPassCount(String output, int totalTests) {
        try {
            if (output == null || output.isBlank()) {
                return 0;
            }
            for (String line : output.split("\\r?\\n")) {
                if (line.trim().startsWith("ASSESSMENT_RESULT|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        int parsed = Integer.parseInt(parts[1].trim());
                        return Math.max(0, Math.min(parsed, totalTests));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse sandbox output for partial credit", e);
        }
        return 0;
    }
}
