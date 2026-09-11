package com.aegis.arena_service.repository;

import com.aegis.arena_service.model.PlayerProfile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerProfileRepository extends MongoRepository<PlayerProfile, String> {
    public List<PlayerProfile> findAllByOrderByCurrentEloDesc(PageRequest pageRequest);
    List<PlayerProfile> findTop10ByOrderByCurrentEloDesc();
}