package com.aegis.arena_service.services;

import com.aegis.arena_service.model.PlayerProfile;
import com.aegis.arena_service.repository.PlayerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerProfileService {

    private final PlayerProfileRepository profileRepository;

    // 1. Get a profile, or create a fresh one if they are new
    public PlayerProfile getOrCreateProfile(String username) {
        return profileRepository.findById(username)
                .orElseGet(() -> profileRepository.save(new PlayerProfile(username)));
    }

    public List<PlayerProfile> getTopRankings() {
        return profileRepository.findAllByOrderByCurrentEloDesc(PageRequest.of(0, 10));
    }

    // 2. Update stats after a match finishes
    // Change signature to accept the calculated Elo values
    public void recordMatchResult(String winnerUsername, String loserUsername,
                                  int newWinnerElo, int newLoserElo) {
        PlayerProfile winner = getOrCreateProfile(winnerUsername);
        PlayerProfile loser = getOrCreateProfile(loserUsername);

        // Winner
        winner.setMatchesPlayed(winner.getMatchesPlayed() + 1);
        winner.setWins(winner.getWins() + 1);
        winner.setCurrentWinStreak(winner.getCurrentWinStreak() + 1);
        if (winner.getCurrentWinStreak() > winner.getHighestWinStreak()) {
            winner.setHighestWinStreak(winner.getCurrentWinStreak());
        }
        winner.setCurrentElo(newWinnerElo); // ✅ real calculated Elo

        // Loser
        loser.setMatchesPlayed(loser.getMatchesPlayed() + 1);
        loser.setLosses(loser.getLosses() + 1);
        loser.setCurrentWinStreak(0);
        loser.setCurrentElo(Math.max(0, newLoserElo)); // ✅ real calculated Elo

        profileRepository.save(winner);
        profileRepository.save(loser);
    }
}