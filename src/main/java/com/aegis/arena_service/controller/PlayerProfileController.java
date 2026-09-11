package com.aegis.arena_service.controller;

import com.aegis.arena_service.model.PlayerProfile;
import com.aegis.arena_service.services.PlayerProfileService;
import com.aegis.arena_service.repository.PlayerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class PlayerProfileController {

    private final PlayerProfileRepository repository;
    private final PlayerProfileService service;

    @GetMapping("/leaderboard")
    public List<PlayerProfile> getLeaderboard() {
        return repository.findTop10ByOrderByCurrentEloDesc();
    }

    @GetMapping("/{username}")
    public ResponseEntity<PlayerProfile> getStats(@PathVariable String username) {
        return ResponseEntity.ok(service.getOrCreateProfile(username));
    }
}