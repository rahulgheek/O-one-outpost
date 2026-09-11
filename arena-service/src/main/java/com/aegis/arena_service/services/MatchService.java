package com.aegis.arena_service.services;

import com.aegis.arena_service.dto.LobbyEvent;
import com.aegis.arena_service.model.Match;
import com.aegis.arena_service.model.Problem;
import com.aegis.arena_service.repository.MatchRepository;
import com.aegis.arena_service.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final ProblemRepository problemRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    public Match joinLobby(String playerId, Integer playerRating) {
        int ratingTolerance = 400;
        int minRating = playerRating - ratingTolerance;
        int maxRating = playerRating + ratingTolerance;

        List<Match> waitingMatches = matchRepository.findByStatusAndPlayerOneRatingBetween(
                "WAITING", minRating, maxRating);

        for (Match match : waitingMatches) {
            if (!match.getPlayerOneId().equals(playerId)) {

                match.setPlayerTwoId(playerId);
                match.setPlayerTwoRating(playerRating);
                match.setStatus("IN_PROGRESS");
                match.setStartTime(LocalDateTime.now());

                int avgRating = (match.getPlayerOneRating() + playerRating) / 2;
                String targetDifficulty = determineDifficulty(avgRating);

                // ✅ FIX: Safe problem selection with fallback chain
                Problem selectedProblem = selectProblem(targetDifficulty);

                match.setProblemId(selectedProblem.getId());

                Match savedMatch = matchRepository.save(match);
                String matchId = savedMatch.getMatchId();

                System.out.println("[MatchService] Match paired! ID: " + matchId
                        + " | Problem: " + selectedProblem.getTitle()
                        + " (" + targetDifficulty + ")");

                messagingTemplate.convertAndSend(
                        "/topic/lobby/" + savedMatch.getPlayerOneId(),
                        new LobbyEvent(matchId, savedMatch.getProblemId(), "MATCH_FOUND")
                );
                messagingTemplate.convertAndSend(
                        "/topic/lobby/" + savedMatch.getPlayerTwoId(),
                        new LobbyEvent(matchId, savedMatch.getProblemId(), "MATCH_FOUND")
                );

                return savedMatch;
            }
        }

        Match newMatch = new Match();
        newMatch.setPlayerOneId(playerId);
        newMatch.setPlayerOneRating(playerRating);
        newMatch.setStatus("WAITING");
        Match saved = matchRepository.save(newMatch);
        System.out.println("[MatchService] No opponent found. Created WAITING match: " + saved.getMatchId());
        return saved;
    }

    /**
     * Tries the target difficulty first, then falls back to ANY available problem.
     * Throws a clear error if the problems collection is completely empty.
     */
    private Problem selectProblem(String targetDifficulty) {
        // 1. Try exact difficulty
        return problemRepository.findRandomByDifficulty(targetDifficulty)
                .or(() -> {
                    // 2. Try any difficulty as fallback
                    System.out.println("[MatchService] No " + targetDifficulty
                            + " problems found. Trying any available problem...");
                    List<Problem> all = problemRepository.findAll();
                    return all.isEmpty() ? java.util.Optional.empty()
                            : java.util.Optional.of(all.get(random.nextInt(all.size())));
                })
                .orElseThrow(() -> new RuntimeException(
                        "No problems found in the database! Seed the problems collection before starting a match."));
    }

    /**
     * Called when a player voluntarily leaves a match.
     * The opponent is declared winner and Elo is updated accordingly.
     */
    public EloResult leaveMatch(String matchId, String playerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if ("COMPLETED".equals(match.getStatus())) {
            // Already over — return current ratings without changing anything
            boolean isP1 = match.getPlayerOneId().equals(playerId);
            return new EloResult(
                    isP1 ? match.getPlayerTwoRating() : match.getPlayerOneRating(),
                    isP1 ? match.getPlayerOneRating() : match.getPlayerTwoRating()
            );
        }

        // The opponent wins by default
        String winnerId = match.getPlayerOneId().equals(playerId)
                ? match.getPlayerTwoId()
                : match.getPlayerOneId();

        return completeMatch(matchId, winnerId);
    }

    private String determineDifficulty(int elo) {
        if (elo < 1200) return "EASY";
        if (elo < 1800) return "MEDIUM";
        return "HARD";
    }

    public record EloResult(int newWinnerElo, int newLoserElo) {}

    public EloResult completeMatch(String matchId, String winnerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if ("COMPLETED".equals(match.getStatus())) {
            boolean isP1Winner = match.getPlayerOneId().equals(winnerId);
            return new EloResult(
                    isP1Winner ? match.getPlayerOneRating() : match.getPlayerTwoRating(),
                    isP1Winner ? match.getPlayerTwoRating() : match.getPlayerOneRating()
            );
        }

        boolean isPlayerOneWinner = match.getPlayerOneId().equals(winnerId);
        int p1Rating = match.getPlayerOneRating();
        int p2Rating = match.getPlayerTwoRating();

        int newP1Rating = calculateNewElo(p1Rating, p2Rating, isPlayerOneWinner ? 1 : 0);
        int newP2Rating = calculateNewElo(p2Rating, p1Rating, isPlayerOneWinner ? 0 : 1);

        match.setStatus("COMPLETED");
        match.setWinnerId(winnerId);
        match.setEndTime(LocalDateTime.now());
        match.setPlayerOneRating(newP1Rating);
        match.setPlayerTwoRating(newP2Rating);

        matchRepository.save(match);

        return new EloResult(
                isPlayerOneWinner ? newP1Rating : newP2Rating,
                isPlayerOneWinner ? newP2Rating : newP1Rating
        );
    }

    private int calculateNewElo(int currentRating, int opponentRating, int actualScore) {
        int kFactor = 32;
        double expectedScore = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - currentRating) / 400.0));
        return (int) Math.round(currentRating + kFactor * (actualScore - expectedScore));
    }
}
