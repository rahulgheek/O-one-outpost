package com.aegis.arena_service.repository;

import com.aegis.arena_service.model.MatchRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchRecordRepository extends MongoRepository<MatchRecord,String> {
    List<MatchRecord> findByWinnerId(String winnerId);
}
