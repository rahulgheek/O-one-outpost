package com.aegis.arena_service.controller;

import com.aegis.arena_service.dto.SandboxRequest;
import com.aegis.arena_service.dto.MatchEvent;
import com.aegis.arena_service.model.Match;
import com.aegis.arena_service.model.PlayerProfile;
import com.aegis.arena_service.repository.MatchRepository;
import com.aegis.arena_service.services.MatchRecordService;
import com.aegis.arena_service.services.MatchService;
import com.aegis.arena_service.services.PlayerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchRecordService matchRecordService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchRepository matchRepository;

    // Injecting the matchmaking engines
    private final MatchService matchService;
    private final PlayerProfileService playerProfileService;

    // --- PHASE 3: THE MATCHMAKER ENDPOINT ---
    @PostMapping("/join")
    public ResponseEntity<Match> joinMatchmaker(@RequestHeader("loggedInUser") String playerId) {
        // 1. Fetch the player's Elo rating (or create a fresh profile if new)
        PlayerProfile profile = playerProfileService.getOrCreateProfile(playerId);

        // 2. Trigger the matchmaking logic in the service layer
        Match match = matchService.joinLobby(playerId, profile.getCurrentElo());

        return ResponseEntity.ok(match);
    }

    // --- CODE SUBMISSION ENDPOINT ---
    @PostMapping("/{matchId}/submit")
    public ResponseEntity<String> submitCode(
            @PathVariable String matchId,
            @RequestHeader("loggedInUser") String playerId,
            @RequestBody SandboxRequest request) {

        MatchRecordService.ExecutionResult result = matchRecordService.executeAndSaveResult(matchId, playerId, request);

        // Broadcast with test progress data
        MatchEvent event = new MatchEvent(playerId, result.status(), result.passedCount(), result.totalCount());
        messagingTemplate.convertAndSend("/topic/match/" + matchId, event);

        return ResponseEntity.ok("Submission processed, saved to DB, and broadcasted.");
    }

    // --- LEAVE MATCH ENDPOINT ---
    @PostMapping("/{matchId}/leave")
    public ResponseEntity<String> leaveMatch(
            @PathVariable String matchId,
            @RequestHeader("loggedInUser") String playerId) {

        // 1. Find the match to determine the opponent
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if ("COMPLETED".equals(match.getStatus())) {
            return ResponseEntity.ok("Match is already completed.");
        }

        String opponentId = match.getPlayerOneId().equals(playerId)
                ? match.getPlayerTwoId()
                : match.getPlayerOneId();

        // 2. Complete the match — opponent wins by default, updates Elo
        MatchService.EloResult eloResult = matchService.leaveMatch(matchId, playerId);

        // 3. Update PlayerProfiles with the new Elo values
        playerProfileService.recordMatchResult(
                opponentId, playerId,
                eloResult.newWinnerElo(), eloResult.newLoserElo()
        );

        // 4. Broadcast the leave event so the opponent sees victory
        MatchEvent event = new MatchEvent(playerId, "LEFT", 0, 0);
        messagingTemplate.convertAndSend("/topic/match/" + matchId, event);

        return ResponseEntity.ok("You have left the match. Opponent wins.");
    }
}