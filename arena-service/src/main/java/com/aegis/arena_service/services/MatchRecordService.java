package com.aegis.arena_service.services;

import com.aegis.arena_service.dto.SandboxRequest;
import com.aegis.arena_service.model.Match;
import com.aegis.arena_service.model.MatchRecord;
import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.MatchRepository;
import com.aegis.arena_service.repository.MatchRecordRepository;
import com.aegis.arena_service.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchRecordService {

    private final RestTemplate restTemplate;
    private final MatchRecordRepository matchRecordRepository;
    private final PlayerProfileService playerProfileService;
    private final MatchService matchService;

    // --- INJECTIONS FOR THE EXECUTION ENGINE ---
    private final MatchRepository matchRepository;
    private final ProblemRepository problemRepository;
    private final CodeGeneratorService codeGeneratorService;
    @Value("${sandbox.url}")
    private String sandboxUrl;

    /**
     * Holds the parsed result from the sandbox execution including test progress.
     */
    public record ExecutionResult(String status, int passedCount, int totalCount) {}

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("PROGRESS\\|(\\d+)/(\\d+)");

    public ExecutionResult executeAndSaveResult(String matchId, String playerId, SandboxRequest request) {

        // 1. Fetch the Match to find out WHICH problem they are solving
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if ("COMPLETED".equals(match.getStatus())) {
            return new ExecutionResult("MATCH_ALREADY_OVER|The battle has already been decided!", 0, 0);
        }

        Problem problem = problemRepository.findById(match.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found"));

        int totalTests = problem.getTestCases() != null ? problem.getTestCases().size() : 0;

        // 2. WRAP THE CODE! Inject the hidden tests and main method
        String rawCode = request.code();
        String languageId = request.language() == null || request.language().isBlank()
                ? "java"
                : request.language().trim().toLowerCase(java.util.Locale.ROOT);
        String executablePayload = codeGeneratorService.generateExecutableCode(rawCode, problem, languageId);

        SandboxRequest updatedRequest = new SandboxRequest(
                executablePayload,              // Wrapped executable code
                problem.getTimeLimitMs(),        // Inject DB time limit
                problem.getMemoryLimitKb(),      // Inject DB memory limit
                languageId                       // Selects the sandbox compiler
        );

        // 3. Forward to Sandbox

        log.info("Forwarding wrapped {} code for {} to Sandbox...", languageId, playerId);

        String sandboxOutput;
        try {
            sandboxOutput = restTemplate.postForObject(sandboxUrl, updatedRequest, String.class);
        } catch (Exception e) {
            return new ExecutionResult("SYSTEM_ERROR|Failed to reach Code Sandbox", 0, totalTests);
        }

        if (sandboxOutput == null) return new ExecutionResult("SYSTEM_ERROR|No response from Sandbox", 0, totalTests);
        log.info("Raw Sandbox Output: \n{}", sandboxOutput);

        // 4. Parse PROGRESS|X/Y from the output
        int passedCount = 0;
        Matcher progressMatcher = PROGRESS_PATTERN.matcher(sandboxOutput);
        if (progressMatcher.find()) {
            passedCount = Integer.parseInt(progressMatcher.group(1));
            totalTests = Integer.parseInt(progressMatcher.group(2));
        }

        // 5. Parse result flags from CodeGeneratorService
        String finalResult;
        if (sandboxOutput.contains("ALL_TESTS_PASSED")) {
            finalResult = "SUCCESS";
        } else if (sandboxOutput.contains("WRONG_ANSWER")) {
            finalResult = sandboxOutput.lines()
                    .filter(line -> line.startsWith("WRONG_ANSWER"))
                    .findFirst()
                    .orElse(sandboxOutput.trim());
        } else if (sandboxOutput.contains("RUNTIME_ERROR")) {
            finalResult = sandboxOutput.lines()
                    .filter(line -> line.startsWith("RUNTIME_ERROR"))
                    .findFirst()
                    .orElse(sandboxOutput.trim());
        } else if (sandboxOutput.contains("COMPILATION_ERROR")) {
            finalResult = sandboxOutput.trim();
        } else if (sandboxOutput.contains("TIME_LIMIT_EXCEEDED")) {
            finalResult = "TIME_LIMIT_EXCEEDED|Your code took too long to execute.";
        } else if (sandboxOutput.contains("MEMORY_LIMIT_EXCEEDED")) {
            finalResult = "MEMORY_LIMIT_EXCEEDED|Your code exceeded the memory limit.";
        } else {
            finalResult = "EXECUTION_FAILED|\n" + sandboxOutput;
        }

        if ("SUCCESS".equals(finalResult)) {
            String opponentId = match.getPlayerOneId().equals(playerId)
                    ? match.getPlayerTwoId()
                    : match.getPlayerOneId();

            // Save the match record only when someone actually won
            MatchRecord record = new MatchRecord();
            record.setMatchId(matchId);
            record.setWinnerId(playerId);
            record.setWinningStatus(finalResult);
            record.setLanguage(languageId);
            record.setSubmittedCode(rawCode);
            record.setTimestamp(LocalDateTime.now());
            matchRecordRepository.save(record);

            // Update Elo
            MatchService.EloResult eloResult = matchService.completeMatch(matchId, playerId);

            // Update PlayerProfiles with real Elo
            playerProfileService.recordMatchResult(
                    playerId, opponentId,
                    eloResult.newWinnerElo(), eloResult.newLoserElo()
            );

            log.info("Player {} won! New Elo: {}", playerId, eloResult.newWinnerElo());
        }

        return new ExecutionResult(finalResult, passedCount, totalTests);
    }
}