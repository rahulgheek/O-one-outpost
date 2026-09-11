package com.aegis.assessment_service.service;

import com.aegis.assessment_service.dto.SandboxRequest;
import com.aegis.assessment_service.entitiy.*;
import com.aegis.assessment_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentExecutionService {

    private final AssessmentRepository assessmentRepository;
    private final CompanyProblemService problemService;
    private final CandidateSubmissionRepository submissionRepository;
    private final LeaderboardRecordRepository leaderboardRepository;
    private final RestTemplate restTemplate;

    private final AssessmentCodeGeneratorService codeGeneratorService;

    @Value("${sandbox.url}")
    private String sandboxUrl;

    @Transactional
    public CandidateSubmission submitCode(String assessmentId, String candidateId, String problemId, String rawCode) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        LocalDateTime now = LocalDateTime.now();

        // 1. STRICT TIME ENFORCEMENT
        if (now.isAfter(assessment.getEndTime())) {
            throw new RuntimeException("Time limit exceeded. Submissions are closed.");
        }

        CompanyProblem problem = problemService.getProblemById(problemId);
        int totalTests = problem.getTestCases().size();

        // 2. Fetch Previous Max Points (Anti-Farming Logic restored)
        int previousMaxPoints = submissionRepository
                .findByAssessmentIdAndCandidateIdAndProblemId(assessmentId, candidateId, problemId)
                .stream()
                .mapToInt(CandidateSubmission::getPointsAwarded)
                .max()
                .orElse(0);

        // 3. Prepare and Execute in Sandbox
        String executablePayload = codeGeneratorService.generateExecutableCode(rawCode, problem);
        SandboxRequest request = new SandboxRequest(executablePayload, problem.getTimeLimitMs(), problem.getMemoryLimitKb());

        String sandboxOutput;
        try {
            sandboxOutput = restTemplate.postForObject(sandboxUrl, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException("SYSTEM_ERROR|Failed to reach Code Sandbox");
        }

        // 4. Determine Status and Scoring
        int passedTests = 0;
        int pointsAwarded = 0;
        String executionStatus;

        if (sandboxOutput != null && sandboxOutput.contains("COMPILATION_ERROR")) {
            // Handle Compile Errors
            executionStatus = sandboxOutput
                    .replace("/app/Main.java:", "Error at Line ")
                    .replace("COMPILATION_ERROR", "COMPILATION FAILED:\n");
        } else {
            // Handle Successful Execution (Normal Flow)
            passedTests = parseSandboxPassCount(sandboxOutput, totalTests);
            pointsAwarded = (int) (((double) passedTests / totalTests) * getDifficultyWeight(problem.getDifficulty()));

            if (passedTests == totalTests) {
                executionStatus = "ALL_PASSED";
            } else if (passedTests > 0) {
                executionStatus = "PARTIAL_SUCCESS";
            } else {
                executionStatus = "FAILED (Check logic or runtime errors)";
            }
        }

        // 5. Create and Save Submission (DRY: Only done once!)
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

        submissionRepository.save(submission);

        // 6. Update Leaderboard (Only if they improved their score!)
        if (pointsAwarded > previousMaxPoints) {
            int netNewPoints = pointsAwarded - previousMaxPoints; // Only add the delta
            updateLeaderboard(assessmentId, candidateId, netNewPoints, assessment.getStartTime(), now);
        }

        return submission;
    }

    private void updateLeaderboard(String assessmentId, String candidateId, int newPoints, LocalDateTime startTime, LocalDateTime submitTime) {
        LeaderboardRecord record = leaderboardRepository
                .findByAssessmentIdAndCandidateId(assessmentId, candidateId)
                .orElse(new LeaderboardRecord(null, assessmentId, candidateId, 0, 0));

        // Add the delta points
        record.setTotalPoints(record.getTotalPoints() + newPoints);

        // Time penalty: Only add penalty when they successfully increase their score
        long timeTakenSeconds = Duration.between(startTime, submitTime).getSeconds();
        record.setTotalTimePenaltySeconds(record.getTotalTimePenaltySeconds() + timeTakenSeconds);

        leaderboardRepository.save(record);
    }

    private int getDifficultyWeight(String difficulty) {
        return switch (difficulty.toUpperCase()) {
            case "EASY" -> 100;
            case "MEDIUM" -> 300;
            case "HARD" -> 500;
            default -> 100;
        };
    }

    // --- NEW: THE PARTIAL CREDIT PARSER ---
    private int parseSandboxPassCount(String output, int totalTests) {
        try {
            if (output == null || output.isBlank()) return 0;

            // Loop through the sandbox logs to find our custom formatted string
            for (String line : output.split("\\r?\\n")) {
                if (line.trim().startsWith("ASSESSMENT_RESULT|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        return Integer.parseInt(parts[1].trim()); // Grabs the passedCount
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse sandbox output for partial credit", e);
        }

        // If it was a compile error or crashed entirely, they get 0
        return 0;
    }
}