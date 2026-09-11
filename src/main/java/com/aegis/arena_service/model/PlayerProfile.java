package com.aegis.arena_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Document(collection = "player_profiles")
public class PlayerProfile {

    @Id
    private String username; // This is the exact String from your SQL Auth Database!

    private int currentElo = 1200; // Standard starting Elo for competitive games
    private int matchesPlayed = 0;
    private int wins = 0;
    private int losses = 0;
    private int currentWinStreak = 0;
    private int highestWinStreak = 0;

    // A handy constructor for when a player logs into the Arena for the very first time
    public PlayerProfile(String username) {
        this.username = username;
    }
}